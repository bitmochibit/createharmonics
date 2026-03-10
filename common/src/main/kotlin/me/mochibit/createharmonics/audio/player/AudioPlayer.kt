package me.mochibit.createharmonics.audio.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.mochibit.createharmonics.audio.comp.SoundEventComposition
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.instance.SampleRatedInstance
import me.mochibit.createharmonics.audio.stream.AudioEffectInputStream
import me.mochibit.createharmonics.foundation.async.modLaunch
import me.mochibit.createharmonics.foundation.async.withMainContext
import me.mochibit.createharmonics.foundation.extension.ticks
import me.mochibit.createharmonics.foundation.network.packet.AudioPlayerStreamEndPacket
import me.mochibit.createharmonics.foundation.registry.ModPackets
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import java.io.Closeable
import java.io.InputStream
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

typealias SoundInstanceFactory = (streamId: String, stream: InputStream) -> SoundInstance

/**
 * [WIP]
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

    private val stateMachineJob: Job

    val effectChain = EffectChain()

    val soundEventComposition = SoundEventComposition()

    private val soundManager get() = Minecraft.getInstance().soundManager

    private var currentSoundInstance: SoundInstance? = null

    val state: StateFlow<PlayerState> = _state.asStateFlow()

    init {
        stateMachineJob =
            modLaunch(Dispatchers.IO) {
                for (intent in intents) {
                    handleIntent(intent)
                }
            }
    }

    private suspend fun handleIntent(intent: PlayerIntent) {
        when (intent) {
            PlayerIntent.AudioFinished -> {
                if (_state.value == PlayerState.STOPPED) return
                notifyStreamEnd()
                stopPlayback()
            }

            PlayerIntent.AudioHanged -> {
                if (_state.value == PlayerState.STOPPED) return
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
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun startPlayback(pos: Double = 0.0) {
        val request = currentAudioRequest ?: return
        transition(PlayerState.LOADING)
        val source = AudioSourceResolver.resolve(request)
        val resolvedInputStream = SourceStreamResolver.resolveInputStream(source, pos)
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
            soundEventComposition.makeComposition(soundInstance)
            soundManager.play(soundInstance)
        }

        currentSoundInstance = soundInstance
        currentAudioEffectInputStream = stream
        transition(PlayerState.PLAYING)
    }

    private suspend fun pausePlayback() {
        val soundInstance =
            currentSoundInstance ?: return transition(PlayerState.STOPPED).also {
                withMainContext {
                    soundEventComposition.stopComposition()
                }
            }

        withMainContext {
            soundManager.stop(soundInstance)
            soundEventComposition.stopComposition()
        }
    }

    private suspend fun resumePlayback() {
        val soundInstance =
            currentSoundInstance ?: return transition(PlayerState.STOPPED).also {
                withMainContext {
                    soundEventComposition.stopComposition()
                }
            }

        withMainContext {
            soundManager.play(soundInstance)
            soundEventComposition.makeComposition(soundInstance)
        }
    }

    private suspend fun unstuckPlayback() {
        val soundInstance = currentSoundInstance ?: return transition(PlayerState.STOPPED)
        delay(1.ticks())
        withMainContext {
            soundManager.play(soundInstance)
        }
    }

    private suspend fun stopPlayback() {
        if (_state.value == PlayerState.STOPPED) return
        currentAudioEffectInputStream?.close().also {
            currentAudioEffectInputStream = null
        }
        withMainContext {
            currentSoundInstance?.let {
                soundManager.stop(it)
            }
            soundEventComposition.stopComposition()
        }
        currentSoundInstance = null
        transition(PlayerState.STOPPED)
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

    override fun close() {
        intents.close()
        stateMachineJob.cancel()
        currentAudioEffectInputStream?.close()
        modLaunch {
            currentSoundInstance?.let { soundManager.stop(it) }
            soundEventComposition.stopComposition()
        }
    }
}
