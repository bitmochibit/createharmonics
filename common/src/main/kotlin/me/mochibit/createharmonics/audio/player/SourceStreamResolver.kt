package me.mochibit.createharmonics.audio.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.mochibit.createharmonics.audio.info.AudioInfo
import me.mochibit.createharmonics.audio.process.FFmpegExecutor
import me.mochibit.createharmonics.audio.source.AudioSource
import me.mochibit.createharmonics.audio.source.HttpAudioSource
import me.mochibit.createharmonics.audio.source.StreamAudioSource
import me.mochibit.createharmonics.audio.source.YtdlpAudioSource
import java.io.InputStream

object SourceStreamResolver {
    data class Result(
        val status: StreamStatus,
        val inputStream: InputStream?,
        val audioInfo: AudioInfo,
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
        val info = source.resolveAudioInfo()
        val adjustedPosition = if (info.isLive) 0.0 else pos
        val result =
            when (source) {
                is HttpAudioSource, is YtdlpAudioSource -> {
                    if (adjustedPosition > 0 && adjustedPosition > info.durationSeconds) {
                        Result(
                            status = Result.StreamStatus.FINISHED,
                            inputStream = null,
                            info,
                        )
                    } else {
                        Result(
                            status = Result.StreamStatus.OK,
                            inputStream =
                                FFmpegExecutor.makeStream(
                                    info.audioUrl,
                                    info.sampleRate.toInt(),
                                    adjustedPosition,
                                    info.httpHeaders,
                                    info.isLive,
                                ),
                            info,
                        )
                    }
                }

                is StreamAudioSource -> {
                    val stream = source.streamRetriever()
                    if (adjustedPosition > 0) {
                        val positionWithinDuration = skipStreamToOffset(stream, adjustedPosition, info.sampleRate.toInt())
                        if (!positionWithinDuration) {
                            return Result(status = Result.StreamStatus.FINISHED, inputStream = null, info)
                        }
                    }
                    return Result(
                        status = Result.StreamStatus.OK,
                        inputStream = stream,
                        info,
                    )
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
