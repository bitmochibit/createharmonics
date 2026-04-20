package me.mochibit.createharmonics.audio.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.mochibit.createharmonics.audio.comp.SoundEventComposition
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.effect.MixerEffect
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
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

typealias SoundInstanceFactory = (streamId: String, stream: InputStream) -> SoundInstance

/**
 * Audio player with the following features:
 * - Play audio from a stream or a url
 * - Idempotent commands
 * - Internal state machine with TAILING state for effect decay
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

    @Volatile private var currentAudioRequest: AudioRequest? = null

    @Volatile private var currentAudioEffectInputStream: AudioEffectInputStream? = null

    @Volatile private var currentSoundInstance: SoundInstance? = null

    private val intents = Channel<PlayerIntent>(Channel.UNLIMITED)

    @Volatile private var stateMachineJob: Job? = null

    @Volatile private var tailJob: Job? = null

    @Volatile private var startPlaybackJob: Job? = null

    private val streamGeneration = AtomicInteger(0)

    val effectChain = EffectChain()
    val soundEventComposition = SoundEventComposition(soundEffectChain = effectChain)
    val state: StateFlow<PlayerState> = _state.asStateFlow()
    val clock = PlaytimeClock()
    val isSeekingDisabled = AtomicBoolean(false)

    private val soundManager get() = Minecraft.getInstance().soundManager

    @Volatile private var lastResyncAt: Long = -1L
    private val resyncCooldown = 10.seconds

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
                startPlaybackJob?.cancelAndJoin() // wait for job to fully exit before notifying
                notifyStreamEnd()
                stopPlayback()
            }

            PlayerIntent.AudioHanged -> {
                if (_state.value == PlayerState.STOPPED) return
                startPlaybackJob?.cancelAndJoin()
                when (_state.value) {
                    PlayerState.LOADING -> {
                        val pos = clock.currentPlaytime
                        doStopPlayback()
                        startPlayback(pos)
                    }

                    PlayerState.PAUSED -> {
                        resumePlayback(freezeOnly = true)
                        pausePlayback()
                    }

                    PlayerState.PLAYING -> {
                        unstuckPlayback()
                    }

                    else -> {}
                }
            }

            PlayerIntent.TailFinished -> {
                if (_state.value == PlayerState.TAILING) {
                    _state.value = PlayerState.STOPPED
                }
            }

            is PlayerIntent.NewRequest -> {
                if (currentAudioRequest?.isSameSource(intent.req) == true) return

                // Cancel in-flight resolution before changing the request
                startPlaybackJob?.cancelAndJoin()

                if (_state.value == PlayerState.PLAYING || _state.value == PlayerState.LOADING) return
                stopPlayback()
                currentAudioRequest = intent.req
            }

            is PlayerIntent.Play -> {
                when (_state.value) {
                    PlayerState.PLAYING,
                    PlayerState.LOADING,
                    -> {
                        return
                    }

                    PlayerState.PAUSED -> {
                        // If there's a live sound instance, resume it.
                        // If not (we were paused mid-load), restart from the clock position.
                        if (currentSoundInstance != null) {
                            resumePlayback()
                        } else {
                            // Was paused before loading finished — treat as a fresh start
                            // from wherever the clock was when we paused.
                            stopPlayback() // clean any partial state
                            startPlayback(clock.currentPlaytime)
                        }
                    }

                    PlayerState.STOPPED -> {
                        startPlayback(intent.initialPosition)
                    }

                    PlayerState.TAILING -> {
                        startPlayback(intent.initialPosition)
                    }

                    PlayerState.HANGED -> {
                        unstuckPlayback()
                    }
                }
            }

            PlayerIntent.Pause -> {
                when (_state.value) {
                    PlayerState.PAUSED,
                    PlayerState.TAILING,
                    -> {
                        return
                    }

                    PlayerState.LOADING -> {
                        startPlaybackJob?.cancelAndJoin()
                        pausePlayback()
                    }

                    PlayerState.PLAYING -> {
                        pausePlayback()
                    }

                    else -> {
                        return
                    }
                }
            }

            PlayerIntent.Stop -> {
                // Cancel any in-flight resolution first so the FFmpeg process is
                // killed before doStopPlayback tears down the rest.
                startPlaybackJob?.cancelAndJoin()
                when (_state.value) {
                    PlayerState.STOPPED -> return
                    else -> stopPlayback()
                }
            }

            is PlayerIntent.Seek -> {
                startPlaybackJob?.cancelAndJoin()
                if (isSeekingDisabled.get()) return
                if (_state.value == PlayerState.PLAYING || _state.value == PlayerState.PAUSED) {
                    stopPlayback()
                    startPlayback(intent.position)
                }
            }

            else -> {}
        }
    }

    private suspend fun startPlayback(pos: Double = 0.0) {
        cancelTail()
        val request = currentAudioRequest ?: return

        startPlaybackJob?.cancelAndJoin()

        transition(PlayerState.LOADING)

        startPlaybackJob =
            playerScope.launch(Dispatchers.IO) {
                val resolutionStart = System.currentTimeMillis()
                var resolvedInputStream: InputStream? = null
                val resolvedStream =
                    try {
                        val source = AudioSourceResolver.resolve(request)
                        val resolved = SourceStreamResolver.resolveInputStream(source, pos)
                        resolvedInputStream = resolved.inputStream
                        resolved
                    } catch (e: CancellationException) {
                        withContext(NonCancellable) {
                            resolvedInputStream?.close()
                            handleStreamFailure()
                        }
                        throw e
                    } catch (e: Exception) {
                        withContext(NonCancellable) {
                            resolvedInputStream?.close()
                            handleStreamFailure()
                        }
                        return@launch
                    }

                if (_state.value != PlayerState.LOADING) {
                    withContext(NonCancellable) {
                        resolvedStream.inputStream?.close()
                        handleStreamFailure()
                    }
                    return@launch
                }

                val resolutionElapsed = (System.currentTimeMillis() - resolutionStart) / 1000.0
                val adjustedPos = if (resolvedStream.audioInfo.isLive) 0.0 else pos + resolutionElapsed
                clock.play(adjustedPos)

                if (resolvedStream.status == SourceStreamResolver.Result.StreamStatus.FINISHED ||
                    resolvedStream.inputStream == null
                ) {
                    withContext(NonCancellable) {
                        resolvedStream.inputStream?.close()
                        if (pos > 0) {
                            "Restarted source playback since it hanged, probably for this url seek is not supported".info()
                        }
                        // In this particular cause probably the input stream creation dropped a timeout
                        // This can happen with very long videos (especially on YouTube) and seeking is problematic there
                        handleStreamFailure(shouldDisableSeek = true)
                    }
                    return@launch
                }

                if (resolvedStream.audioInfo.isLive) {
                    effectChain.getEffects().filterIsInstance<PitchShiftEffect>().forEach { it.preserveTiming() }
                    isSeekingDisabled.set(true)
                }

                val generation = streamGeneration.incrementAndGet()
                val stream =
                    AudioEffectInputStream(
                        resolvedStream.inputStream,
                        effectChain,
                        resolvedStream.audioInfo.sampleRate.toInt(),
                        onStreamEnd = { if (streamGeneration.get() == generation) handleStreamEnd() },
                        onStreamHang = { if (streamGeneration.get() == generation) handleStreamHang() },
                    )

                currentAudioEffectInputStream = stream

                val soundInstance = soundInstanceFactory(playerId, stream)
                if (soundInstance is SampleRatedInstance) {
                    soundInstance.sampleRate = resolvedStream.audioInfo.sampleRate.toInt()
                }
                currentSoundInstance = soundInstance

                withMainContext { soundManager.play(soundInstance) }
                soundEventComposition.makeComposition(soundInstance)
                notifyAudioTitle(resolvedStream.audioInfo.title)
                transition(PlayerState.PLAYING)
            }
    }

    private suspend fun pausePlayback() {
        val capturedInstance = currentSoundInstance ?: return handleStreamFailure()
        val capturedStream = currentAudioEffectInputStream

        clock.pause()
        transition(PlayerState.PAUSED)
        if (capturedStream?.hasTail == true) {
            capturedStream.resetTailSignal()
            capturedStream.isFrozen = true
            freezeMixerSources(true)
            tailJob =
                modLaunch(Dispatchers.IO) {
                    capturedStream.tailFinished.await()
                    withMainContext {
                        if (!capturedInstance.pause()) soundManager.stop(capturedInstance)
                    }
                }
        } else {
            capturedStream?.isFrozen = true
            freezeMixerSources(true)
            withMainContext {
                if (!capturedInstance.pause()) soundManager.stop(capturedInstance)
            }
        }
    }

    private suspend fun resumePlayback(freezeOnly: Boolean = false) {
        cancelTail()
        val capturedInstance = currentSoundInstance ?: return handleStreamFailure()

        effectChain.setFrozen(false)
        currentAudioEffectInputStream?.isFrozen = false

        if (freezeOnly) {
            currentAudioEffectInputStream?.isFrozen = true
            effectChain.setFrozen(true)
        }

        withMainContext {
            if (!capturedInstance.unpause()) soundManager.play(capturedInstance)
        }
        clock.play()
        transition(PlayerState.PLAYING)
    }

    private suspend fun unstuckPlayback() {
        cancelTail()
        val capturedInstance = currentSoundInstance ?: return handleStreamFailure()
        withMainContext { soundManager.play(capturedInstance) }
    }

    private suspend fun stopPlayback() {
        if (_state.value == PlayerState.STOPPED) return
        doStopPlayback()
    }

    private suspend fun doStopPlayback() {
        cancelTail()
        startPlaybackJob?.cancelAndJoin()
        startPlaybackJob = null

        val capturedStream = currentAudioEffectInputStream
        val capturedInstance = currentSoundInstance
        currentAudioEffectInputStream = null
        currentSoundInstance = null

        soundEventComposition.stopComposition()
        clock.stop()
        isSeekingDisabled.set(false)
        lastResyncAt = -1L

        if (capturedStream?.hasTail == true) {
            _state.value = PlayerState.TAILING
            capturedStream.isFrozen = true
            tailJob =
                modLaunch(Dispatchers.IO) {
                    try {
                        capturedStream.tailFinished.await()
                    } finally {
                        withContext(NonCancellable) {
                            capturedStream.close()
                            effectChain.reset()
                            withMainContext { capturedInstance?.let { soundManager.stop(it) } }
                        }
                    }
                    intents.trySend(PlayerIntent.TailFinished)
                }
        } else {
            capturedStream?.close()
            effectChain.reset()
            withMainContext { capturedInstance?.let { soundManager.stop(it) } }
            _state.value = PlayerState.STOPPED
        }
    }

    private suspend fun handleStreamFailure(shouldDisableSeek: Boolean = false) {
        startPlaybackJob = null
        doStopPlayback()
        isSeekingDisabled.set(shouldDisableSeek)
        intents.trySend(PlayerIntent.Play(0.0))
    }

    private suspend fun cancelTail() {
        tailJob?.cancelAndJoin()
        tailJob = null
    }

    private fun transition(next: PlayerState) {
        val current = _state.value
        check(current.canTransitionTo(next)) { "Invalid transition: $current → $next" }
        _state.value = next
    }

    private fun freezeMixerSources(frozen: Boolean) {
        effectChain
            .getEffects()
            .filterIsInstance<MixerEffect>()
            .forEach { it.setFrozen(frozen) }
    }

    private fun handleStreamEnd() = intents.trySend(PlayerIntent.AudioFinished)

    private fun handleStreamHang() = intents.trySend(PlayerIntent.AudioHanged)

    private fun notifyAudioTitle(name: String) = ModPackets.sendToServer(UpdateAudioNamePacket(playerId, name))

    private fun notifyStreamEnd() = ModPackets.sendToServer(AudioPlayerStreamEndPacket(playerId))

    fun play(initialPosition: Double = 0.0) = intents.trySend(PlayerIntent.Play(initialPosition))

    fun pause() {
        intents.trySend(PlayerIntent.Pause)
    }

    fun stop() {
        intents.trySend(PlayerIntent.Stop)
    }

    fun seek(position: Double) {
        intents.trySend(PlayerIntent.Seek(position))
    }

    fun request(req: AudioRequest) = intents.trySend(PlayerIntent.NewRequest(req))

    fun syncWith(other: PlaytimeClock) {
        if (!clock.isPlaying || !other.isPlaying) return
        val now = System.currentTimeMillis()
        if (lastResyncAt != -1L && now - lastResyncAt < resyncCooldown.inWholeMilliseconds) return
        if (abs(other.currentPlaytime - clock.currentPlaytime) > 5.0) {
            lastResyncAt = now
            seek(other.currentPlaytime)
        }
    }

    fun tick() = clock.tick()

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
