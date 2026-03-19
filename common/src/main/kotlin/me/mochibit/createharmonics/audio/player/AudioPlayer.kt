package me.mochibit.createharmonics.audio.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import me.mochibit.createharmonics.audio.comp.SoundEventComposition
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.instance.SampleRatedInstance
import me.mochibit.createharmonics.audio.stream.AudioEffectInputStream
import me.mochibit.createharmonics.audio.utils.pause
import me.mochibit.createharmonics.audio.utils.unpause
import me.mochibit.createharmonics.foundation.async.modLaunch
import me.mochibit.createharmonics.foundation.async.withMainContext
import me.mochibit.createharmonics.foundation.extension.ticks
import me.mochibit.createharmonics.foundation.info
import me.mochibit.createharmonics.foundation.network.packet.AudioPlayerStreamEndPacket
import me.mochibit.createharmonics.foundation.registry.ModPackets
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import java.io.Closeable
import java.io.InputStream
import kotlin.math.abs

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
) : Closeable {
    private val _state = MutableStateFlow(PlayerState.STOPPED)

    private var currentAudioRequest: AudioRequest? = null

    private var currentAudioEffectInputStream: AudioEffectInputStream? = null

    private val intents = Channel<PlayerIntent>(Channel.UNLIMITED)

    private var stateMachineJob: Job? = null

    val effectChain = EffectChain()

    val soundEventComposition = SoundEventComposition(soundEffectChain = effectChain)

    private val soundManager get() = Minecraft.getInstance().soundManager

    private var currentSoundInstance: SoundInstance? = null

    val state: StateFlow<PlayerState> = _state.asStateFlow()

    val clock = PlaytimeClock()

    init {
        startStateMachine()
    }

    fun startStateMachine() {
        val currentSm = stateMachineJob
        if (currentSm == null || !currentSm.isActive) {
            stateMachineJob =
                modLaunch(Dispatchers.IO) {
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
                        doStopPlayback()
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
                if (_state.value == PlayerState.STOPPED || _state.value == PlayerState.PAUSED) return
                if (_state.value == PlayerState.PLAYING) {
                    unstuckPlayback()
                } else {
                    notifyStreamFailure()
                    stopPlayback()
                }
            }

            is PlayerIntent.NewRequest -> {
                currentAudioRequest = intent.req
                if (_state.value == PlayerState.PLAYING) {
                    stopPlayback()
                    startPlayback()
                }
            }

            PlayerIntent.Play -> {
                when (_state.value) {
                    PlayerState.PLAYING, PlayerState.LOADING -> {
                        return
                    }

                    PlayerState.PAUSED -> {
                        resumePlayback()
                    }

                    PlayerState.STOPPED -> {
                        startPlayback()
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
                    PlayerState.PAUSED -> return
                    PlayerState.PLAYING -> pausePlayback()
                    else -> return
                }
            }

            PlayerIntent.Stop -> {
                when (_state.value) {
                    PlayerState.STOPPED -> return
                    else -> stopPlayback()
                }
            }

            is PlayerIntent.Seek -> {
                if (_state.value == PlayerState.PLAYING || _state.value == PlayerState.PAUSED) {
                    stopPlayback()
                    startPlayback(intent.position)
                }
            }

            else -> {}
        }
    }

    private suspend fun startPlayback(pos: Double = 0.0) {
        val request = currentAudioRequest ?: return
        transition(PlayerState.LOADING)
        val resolvedInputStream =
            try {
                val source = AudioSourceResolver.resolve(request)
                SourceStreamResolver.resolveInputStream(source, pos)
            } catch (e: Exception) {
                "Something went wrong when creating the source $e".info()
                handleStreamEnd()
                return transition(PlayerState.STOPPED)
            }
        if (resolvedInputStream.status == SourceStreamResolver.Result.StreamStatus.FINISHED || resolvedInputStream.inputStream == null) {
            handleStreamEnd()
            return transition(PlayerState.STOPPED)
        }
        val stream =
            AudioEffectInputStream(
                resolvedInputStream.inputStream,
                effectChain,
                resolvedInputStream.finalSampleRate,
                ::handleStreamEnd,
                ::handleStreamHang,
            )

        val soundInstance = soundInstanceFactory(playerId, stream)
        if (soundInstance is SampleRatedInstance) {
            soundInstance.sampleRate = resolvedInputStream.finalSampleRate
        }
        withMainContext {
            soundManager.play(soundInstance)
        }
        soundEventComposition.makeComposition(soundInstance)

        currentSoundInstance = soundInstance
        currentAudioEffectInputStream = stream
        clock.play(pos)
        transition(PlayerState.PLAYING)
    }

    private suspend fun pausePlayback() {
        val soundInstance =
            currentSoundInstance ?: return transition(PlayerState.STOPPED).also {
                soundEventComposition.stopComposition()
            }

        soundInstance.pause()
        soundEventComposition.stopComposition()

        clock.pause()
        transition(PlayerState.PAUSED)
    }

    private suspend fun resumePlayback() {
        val soundInstance =
            currentSoundInstance ?: return transition(PlayerState.STOPPED).also {
                withMainContext {
                    soundEventComposition.stopComposition()
                }
            }

        soundInstance.unpause()
        soundEventComposition.makeComposition(soundInstance)
        clock.play()
        transition(PlayerState.PLAYING)
    }

    private suspend fun unstuckPlayback() {
        val soundInstance = currentSoundInstance ?: return transition(PlayerState.STOPPED)
        withMainContext {
            soundManager.play(soundInstance)
        }
    }

    private suspend fun stopPlayback() {
        if (_state.value == PlayerState.STOPPED) return
        doStopPlayback()
    }

    private suspend fun doStopPlayback() {
        currentAudioEffectInputStream?.close()
        currentAudioEffectInputStream = null
        withMainContext {
            currentSoundInstance?.let { soundManager.stop(it) }
        }
        soundEventComposition.stopComposition()
        currentSoundInstance = null
        effectChain.reset()
        clock.stop()
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

    private fun notifyStreamEnd() = ModPackets.sendToServer(AudioPlayerStreamEndPacket(playerId))

    private fun notifyStreamFailure() = ModPackets.sendToServer(AudioPlayerStreamEndPacket(playerId, true))

    // Public API

    fun play() {
        intents.trySend(PlayerIntent.Play)
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
        if (!clock.isPlaying) return
        if (abs(other.currentPlaytime - clock.currentPlaytime) > 5) {
            seek(other.currentPlaytime)
        }
    }

    override fun close() {
        intents.trySend(PlayerIntent.Shutdown)
        intents.close()
    }
}
