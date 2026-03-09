package me.mochibit.createharmonics.audio.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import me.mochibit.createharmonics.audio.AudioPlayer.Companion.DIRECT_AUDIO_EXTENSIONS
import me.mochibit.createharmonics.audio.StreamExhaustedException
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.process.FFmpegExecutor
import me.mochibit.createharmonics.audio.source.AudioSource
import me.mochibit.createharmonics.audio.source.HttpAudioSource
import me.mochibit.createharmonics.audio.source.StreamAudioSource
import me.mochibit.createharmonics.audio.source.YtdlpAudioSource
import me.mochibit.createharmonics.audio.stream.AudioEffectInputStream
import me.mochibit.createharmonics.foundation.async.modLaunch
import me.mochibit.createharmonics.foundation.warn
import java.io.Closeable
import java.io.InputStream

/**
 * [WIP]
 * Audio player with the following features:
 * - Play audio from a stream or a url
 * - Idempotent commands
 * - Internal state machine
 * - Supports synchronization
 */
class AudioPlayerNew : Closeable {
    private val _state = MutableStateFlow(PlayerState.STOPPED)
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var currentAudioRequest: AudioRequest? = null

    private var currentAudioEffectInputStream: AudioEffectInputStream? = null

    private val intents = Channel<PlayerIntent>(Channel.UNLIMITED)

    val effectChain = EffectChain()

    private val stateMachineJob: Job

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
            is PlayerIntent.NewRequest -> {
                currentAudioRequest = intent.req
                if (_state.value == PlayerState.PLAYING) {
                    stopPlayback()
                    startPlayback()
                }
            }

            PlayerIntent.Play -> {
                when (_state.value) {
                    PlayerState.PLAYING, PlayerState.LOADING -> return
                    PlayerState.PAUSED -> resumePlayback()
                    PlayerState.STOPPED -> startPlayback()
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
                    TODO()
                }
            }
        }
    }

    private suspend fun startPlayback(pos: Double = 0.0) {
        val request = currentAudioRequest ?: return transition(PlayerState.STOPPED)
        transition(PlayerState.PAUSED)
        val source = AudioSourceResolver.resolve(request)
        val resolvedInputStream = SourceStreamResolver.resolveInputStream(source, pos)
        if (resolvedInputStream.status == SourceStreamResolver.Result.StreamStatus.FINISHED || resolvedInputStream.inputStream == null) {
            handleStreamEnd()
            return transition(PlayerState.STOPPED)
        }
        val stream = AudioEffectInputStream(resolvedInputStream.inputStream, effectChain, resolvedInputStream.finalSampleRate)
    }

    private fun pausePlayback() {
    }

    private fun resumePlayback() {
    }

    private fun stopPlayback() {
    }

    private fun transition(next: PlayerState) {
        val current = _state.value
        check(current.canTransitionTo(next)) {
            "Invalid transition detected! $current TO $next"
        }
        _state.value = next
    }

    private fun handleStreamEnd() {
    }

    private fun handleStreamHang() {
    }

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

    fun seek(position: Long) {
        intents.trySend(PlayerIntent.Seek(position))
    }

    fun request(req: AudioRequest) {
        intents.trySend(PlayerIntent.NewRequest(req))
    }

    override fun close() {
        intents.close()
        stateMachineJob.cancel()
        currentAudioEffectInputStream?.close()
    }
}

enum class PlayerState {
    LOADING,
    PLAYING,
    PAUSED,
    STOPPED,
    ;

    fun canTransitionTo(next: PlayerState): Boolean =
        when (this) {
            STOPPED -> next == LOADING
            LOADING -> next == PLAYING || next == STOPPED
            PLAYING -> next == PAUSED || next == STOPPED
            PAUSED -> next == PLAYING || next == STOPPED
        }
}

object AudioSourceResolver {
    private val DIRECT_AUDIO_EXTENSIONS =
        setOf(
            "mp3",
            "mp4",
            "m4a",
            "m4b",
            "wav",
            "wave",
            "ogg",
            "oga",
            "opus",
            "flac",
            "aac",
            "webm",
            "wma",
            "aiff",
            "aif",
        )

    private fun isDirectAudioUrl(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#')
        return path.substringAfterLast('.', "").lowercase() in DIRECT_AUDIO_EXTENSIONS
    }

    fun resolve(request: AudioRequest): AudioSource =
        when (request) {
            is AudioRequest.Stream -> {
                StreamAudioSource(request.streamRetriever, request.streamInfo)
            }

            is AudioRequest.Url -> {
                if (isDirectAudioUrl(request.url)) {
                    HttpAudioSource(request.url)
                } else {
                    YtdlpAudioSource(request.url)
                }
            }
        }
}

object SourceStreamResolver {
    data class Result(
        val status: StreamStatus,
        val inputStream: InputStream?,
        val finalSampleRate: Int,
    ) {
        enum class StreamStatus {
            FAILED,
            FINISHED,
            OK,
        }
    }

    /**
     * Resolves audio source and tries to build an [InputStream] from it
     *
     * If it can't make it will simply return null.
     *
     * If needed, the returned [InputStream] can be seeked to a certain position
     */
    suspend fun resolveInputStream(
        source: AudioSource,
        pos: Double = 0.0,
    ): Result {
        val result =
            when (source) {
                is HttpAudioSource, is YtdlpAudioSource -> {
                    if (pos > 0 && pos > source.getDurationSeconds()) {
                        Result(
                            status = Result.StreamStatus.FINISHED,
                            inputStream = null,
                            0.0,
                        )
                    } else {
                        Result(
                            status = Result.StreamStatus.OK,
                            inputStream =
                                FFmpegExecutor.makeStream(
                                    source.resolveAudioUrl(),
                                    source.getSampleRate(),
                                    pos,
                                    source.getHttpHeaders(),
                                ),
                            finalSampleRate = source.getSampleRate(),
                        )
                    }
                }

                is StreamAudioSource -> {
                    val stream = source.streamRetriever()
                    if (pos > 0) {
                        val positionWithinDuration = skipStreamToOffset(stream, pos, source.getSampleRate())
                        if (!positionWithinDuration) {
                            return Result(status = Result.StreamStatus.FINISHED, inputStream = null, 0)
                        }
                    }
                    return Result(status = Result.StreamStatus.OK, inputStream = stream, finalSampleRate = source.getSampleRate())
                }
            }
        return result
    }

    private suspend fun skipStreamToOffset(
        inputStream: InputStream,
        offsetSeconds: Double,
        effectiveSampleRate: Int,
    ): Boolean {
        val bytesPerSecond = effectiveSampleRate * 2L // 16-bit mono → 2 bytes/sample
        var bytesToSkip = (offsetSeconds * bytesPerSecond).toLong()
        if (bytesToSkip % 2 != 0L) {
            bytesToSkip += 1
        }

        val buf = ByteArray(8192)
        var totalSkipped = 0L
        var remaining = bytesToSkip

        return withContext(Dispatchers.IO) {
            while (remaining > 0) {
                val toRead = minOf(remaining, buf.size.toLong()).toInt()
                val read = inputStream.read(buf, 0, toRead)
                if (read <= 0) {
                    return@withContext false
                }
                totalSkipped += read
                remaining -= read
            }
            return@withContext true
        }
    }
}

sealed interface AudioRequest {
    data class Url(
        val url: String,
    ) : AudioRequest

    data class Stream(
        val streamRetriever: () -> InputStream,
        val streamInfo: StreamAudioSource.Information,
    ) : AudioRequest
}

sealed interface PlayerIntent {
    data object Play : PlayerIntent

    data object Pause : PlayerIntent

    data object Stop : PlayerIntent

    data class Seek(
        val position: Long,
    ) : PlayerIntent

    data class NewRequest(
        val req: AudioRequest,
    ) : PlayerIntent
}
