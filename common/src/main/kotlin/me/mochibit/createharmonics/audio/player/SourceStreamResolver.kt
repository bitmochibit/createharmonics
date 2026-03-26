package me.mochibit.createharmonics.audio.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
                    if (pos > 0 && pos > source.getDurationSeconds() && !source.isLive()) {
                        Result(
                            status = Result.StreamStatus.FINISHED,
                            inputStream = null,
                            0,
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
                                    source.isLive(),
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
                    return Result(
                        status = Result.StreamStatus.OK,
                        inputStream = stream,
                        finalSampleRate = source.getSampleRate(),
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
