package me.mochibit.createharmonics.audio.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.mochibit.createharmonics.audio.comp.SoundEventComposition
import me.mochibit.createharmonics.audio.effect.AudioEffect
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.effect.PitchShiftEffect
import me.mochibit.createharmonics.audio.instance.SampleRatedInstance
import me.mochibit.createharmonics.audio.stream.AudioEffectInputStream
import me.mochibit.createharmonics.audio.utils.pause
import me.mochibit.createharmonics.audio.utils.unpause
import me.mochibit.createharmonics.foundation.async.ClientCoroutineScope
import me.mochibit.createharmonics.foundation.async.modLaunch
import me.mochibit.createharmonics.foundation.async.withMainContext
import me.mochibit.createharmonics.foundation.info
import me.mochibit.createharmonics.foundation.network.packet.AudioPlayerStreamEndPacket
import me.mochibit.createharmonics.foundation.network.packet.UpdateAudioNamePacket
import me.mochibit.createharmonics.foundation.registry.ModPackets
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import java.io.Closeable
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

typealias SoundInstanceFactory = (streamId: String, stream: InputStream) -> SoundInstance

/**
 * Audio player with the following features:
 * - Play audio from a stream or a url
 * - Idempotent commands
 * - Internal state machine
 * - Supports synchronization
 */
class AudioPlayer(
    val playerId: String,
    val soundInstanceFactory: (streamId: String, stream: InputStream) -> SoundInstance,
) {
    private val playerScope =
        CoroutineScope(
            ClientCoroutineScope.coroutineContext + SupervisorJob(ClientCoroutineScope.coroutineContext[Job]),
        )

    private val _state = MutableStateFlow(PlayerState.STOPPED)

    private var currentAudioRequest: AudioRequest? = null

    private var currentAudioEffectInputStream: AudioEffectInputStream? = null

    private val intents = Channel<PlayerIntent>(Channel.UNLIMITED)

    private var stateMachineJob: Job? = null

    val effectChain = EffectChain()

    val soundEventComposition = SoundEventComposition(soundEffectChain = effectChain)

    private val soundManager get() = Minecraft.getInstance().soundManager

    private var currentSoundInstance: SoundInstance? = null

    private var lastResyncAt: Long = -1L
    private val resyncCooldown = 10.seconds

    private var ambientTailJob: Job? = null

    val state: StateFlow<PlayerState> = _state.asStateFlow()

    val clock = PlaytimeClock()

    val reproducingALive =
        AtomicBoolean(false)

    init {
        startStateMachine()
    }

    fun startStateMachine() {
        val currentSm = stateMachineJob
        if (currentSm == null || !currentSm.isActive) {
            stateMachineJob =
                playerScope.launch(Dispatchers.IO) {
                    try {
                        for (intent in intents) {
                            try {
                                if (intent is PlayerIntent.Shutdown) break
                                handleIntent(intent)
                            } catch (e: CancellationException) {
                                if (!isActive) throw e
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    } finally {
                        withContext(NonCancellable) {
                            doStopPlayback()
                            playerScope.cancel()
                        }
                    }
                }
        }
    }

    private suspend fun handleIntent(intent: PlayerIntent) {
        when (intent) {
            PlayerIntent.AudioFinished -> {
                notifyStreamEnd()
                stopPlayback()
            }

            PlayerIntent.AudioHanged -> {
                if (_state.value == PlayerState.STOPPED) return
                if (_state.value == PlayerState.PAUSED) {
                    resumePlayback(true)
                    pausePlayback()
                }
                if (_state.value == PlayerState.PLAYING) {
                    unstuckPlayback()
                }
            }

            is PlayerIntent.NewRequest -> {
                currentAudioRequest = intent.req
                if (_state.value == PlayerState.PLAYING) {
                    stopPlayback()
                    startPlayback()
                }
            }

            is PlayerIntent.Play -> {
                when (_state.value) {
                    PlayerState.PLAYING, PlayerState.LOADING -> {
                        return
                    }

                    PlayerState.PAUSED -> {
                        resumePlayback()
                    }

                    PlayerState.STOPPED -> {
                        startPlayback(intent.initialPosition)
                    }

                    PlayerState.HANGED -> {
                        unstuckPlayback()
                    }

                    PlayerState.FINISHING -> {
                        stopPlayback()
                    }
                }
            }

            PlayerIntent.Pause -> {
                when (_state.value) {
                    PlayerState.PAUSED -> {
                        return
                    }

                    PlayerState.PLAYING -> {
                        pausePlayback()
                    }

                    PlayerState.LOADING -> {
                        transition(PlayerState.PAUSED)
                    }

                    else -> {
                        return
                    }
                }
            }

            PlayerIntent.Stop -> {
                when (_state.value) {
                    PlayerState.STOPPED -> return
                    else -> stopPlayback()
                }
            }

            is PlayerIntent.Seek -> {
                if (this.reproducingALive.get()) return
                if (_state.value == PlayerState.PLAYING || _state.value == PlayerState.PAUSED) {
                    stopPlayback()
                    startPlayback(intent.position)
                }
            }

            else -> {}
        }
    }

    private suspend fun startPlayback(pos: Double = 0.0) {
        cancelTailWaiting()
        val request = currentAudioRequest ?: return
        transition(PlayerState.LOADING)
        val resolutionStart = System.currentTimeMillis()
        val resolvedInputStream =
            try {
                val source =
                    runInterruptible {
                        AudioSourceResolver.resolve(request)
                    }
                SourceStreamResolver.resolveInputStream(source, pos)
            } catch (e: CancellationException) {
                if (_state.value == PlayerState.LOADING) _state.value = PlayerState.STOPPED
                throw e
            } catch (e: Exception) {
                handleStreamEnd()
                return transition(PlayerState.STOPPED)
            }

        val resolutionElapsed = (System.currentTimeMillis() - resolutionStart) / 1000.0
        val adjustedPos = if (resolvedInputStream.audioInfo.isLive) 0.0 else pos + resolutionElapsed

        if (resolvedInputStream.status == SourceStreamResolver.Result.StreamStatus.FINISHED || resolvedInputStream.inputStream == null) {
            handleStreamEnd()
            return transition(PlayerState.STOPPED)
        }

        if (resolvedInputStream.audioInfo.isLive) {
            effectChain.getEffects().filterIsInstance<PitchShiftEffect>().forEach {
                it.preserveTiming()
            }
            reproducingALive.set(true)
        }

        if (!currentCoroutineContext().isActive || _state.value != PlayerState.LOADING) {
            withContext(Dispatchers.IO) {
                resolvedInputStream.inputStream.close()
            }
            if (_state.value == PlayerState.LOADING) transition(PlayerState.STOPPED)
            return
        }

        val stream =
            AudioEffectInputStream(
                resolvedInputStream.inputStream,
                effectChain,
                resolvedInputStream.audioInfo.sampleRate.toInt(),
                ::handleStreamEnd,
                ::handleStreamHang,
            )

        val soundInstance = soundInstanceFactory(playerId, stream)
        if (soundInstance is SampleRatedInstance) {
            soundInstance.sampleRate = resolvedInputStream.audioInfo.sampleRate.toInt()
        }
        withMainContext {
            soundManager.play(soundInstance)
        }
        soundEventComposition.makeComposition(soundInstance)

        currentSoundInstance = soundInstance
        currentAudioEffectInputStream = stream
        clock.play(adjustedPos)
        notifyAudioTitle(resolvedInputStream.audioInfo.title)
        transition(PlayerState.PLAYING)
    }

    private suspend fun pausePlayback() {
        val soundInstance =
            currentSoundInstance ?: return doStopPlayback()

        clock.pause()
        transition(PlayerState.PAUSED)
        val hasTail = currentAudioEffectInputStream?.hasTail ?: false
        if (hasTail) {
            currentAudioEffectInputStream?.isFrozen = true
            ambientTailJob =
                modLaunch(Dispatchers.IO) {
                    currentAudioEffectInputStream?.tailFinished?.await() ?: return@modLaunch
                    withMainContext {
                        if (!soundInstance.pause()) {
                            soundManager.stop(soundInstance)
                        }
                    }
                }
        } else {
            withMainContext {
                if (!soundInstance.pause()) {
                    soundManager.stop(soundInstance)
                }
            }
        }
    }

    private suspend fun resumePlayback(freezeOnly: Boolean = false) {
        cancelTailWaiting()
        val soundInstance =
            currentSoundInstance ?: return doStopPlayback()
        if (freezeOnly) {
            currentAudioEffectInputStream?.isFrozen = true
        }
        withMainContext {
            if (!soundInstance.unpause()) {
                soundManager.play(soundInstance)
            }
        }
        clock.play()
        transition(PlayerState.PLAYING)
    }

    private suspend fun unstuckPlayback() {
        cancelTailWaiting()
        if (_state.value != PlayerState.PLAYING && _state.value != PlayerState.HANGED) return
        val soundInstance = currentSoundInstance ?: return doStopPlayback()
        withMainContext {
            if (!soundManager.isActive(soundInstance)) {
                soundManager.play(soundInstance)
            }
        }
    }

    private suspend fun stopPlayback() {
        if (_state.value == PlayerState.STOPPED) return
        doStopPlayback()
    }

    private suspend fun doStopPlayback() {
        cancelTailWaiting()
        val capturedStream = currentAudioEffectInputStream
        val capturedInstance = currentSoundInstance
        currentAudioEffectInputStream = null
        currentSoundInstance = null

        val hasTail = capturedStream?.hasTail ?: false
        if (hasTail) {
            capturedStream.isFrozen = true
            ambientTailJob =
                modLaunch(Dispatchers.IO) {
                    capturedStream.tailFinished.await()
                    capturedStream.close()
                    effectChain.reset()
                    withMainContext { capturedInstance?.let { soundManager.stop(it) } }
                }
        } else {
            capturedStream?.close()
            effectChain.reset()
            withMainContext { capturedInstance?.let { soundManager.stop(it) } }
        }

        soundEventComposition.stopComposition()
        clock.stop()
        reproducingALive.set(false)
        lastResyncAt = -1L
        _state.value = PlayerState.STOPPED
    }

    private fun transition(next: PlayerState) {
        val current = _state.value
        check(current.canTransitionTo(next)) {
            "Invalid transition detected! $current TO $next"
        }
        _state.value = next
    }

    private fun handleStreamEnd() {
        intents.trySend(PlayerIntent.AudioFinished)
    }

    private fun handleStreamHang() {
        intents.trySend(PlayerIntent.AudioHanged)
    }

    private suspend fun cancelTailWaiting() {
        val wasManagingTail = ambientTailJob != null
        ambientTailJob?.cancel()
        ambientTailJob = null
        currentAudioEffectInputStream?.isFrozen = false
        currentAudioEffectInputStream?.resetTailSignal()

        if (wasManagingTail && _state.value == PlayerState.STOPPED) {
            currentAudioEffectInputStream?.close()
            effectChain.reset()
            withMainContext { currentSoundInstance?.let { soundManager.stop(it) } }
            currentAudioEffectInputStream = null
            currentSoundInstance = null
        }
    }

    private fun notifyAudioTitle(name: String) = ModPackets.sendToServer(UpdateAudioNamePacket(playerId, name))

    private fun notifyStreamEnd() = ModPackets.sendToServer(AudioPlayerStreamEndPacket(playerId))

    private fun notifyStreamFailure() = ModPackets.sendToServer(AudioPlayerStreamEndPacket(playerId, true))

    // Public API

    fun play(initialPosition: Double = 0.0) {
        intents.trySend(PlayerIntent.Play(initialPosition))
    }

    fun pause() {
        intents.trySend(PlayerIntent.Pause)
    }

    fun stop() {
        intents.trySend(PlayerIntent.Stop)
    }

    fun seek(position: Double) {
        intents.trySend(PlayerIntent.Seek(position))
    }

    fun request(req: AudioRequest) {
        intents.trySend(PlayerIntent.NewRequest(req))
    }

    fun syncWith(other: PlaytimeClock) {
        if (!clock.isPlaying || !other.isPlaying) return
        val now = System.currentTimeMillis()
        if (lastResyncAt != -1L && now - lastResyncAt < resyncCooldown.inWholeMilliseconds) return
        if (abs(other.currentPlaytime - clock.currentPlaytime) > 5.0) {
            lastResyncAt = now
            seek(other.currentPlaytime)
        }
    }

    fun tick() {
        this.clock.tick()
    }

    fun close() {
        stateMachineJob?.cancel()
        intents.trySend(PlayerIntent.Shutdown)
        intents.close()
    }

    suspend fun closeSuspending() {
        intents.close()
        withContext(NonCancellable) { doStopPlayback() }
        playerScope.cancel()
    }
}
