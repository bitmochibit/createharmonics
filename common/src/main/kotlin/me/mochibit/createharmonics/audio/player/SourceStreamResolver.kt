package me.mochibit.createharmonics.audio.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.mochibit.createharmonics.audio.info.AudioInfo
import me.mochibit.createharmonics.audio.process.FFmpegExecutor
import me.mochibit.createharmonics.audio.source.AudioSource
import me.mochibit.createharmonics.audio.source.HttpAudioSource
import me.mochibit.createharmonics.audio.source.ServerAudioSource
import me.mochibit.createharmonics.audio.source.StreamAudioSource
import me.mochibit.createharmonics.config.ClientConfig
import me.mochibit.createharmonics.foundation.debug
import java.io.InputStream

object SourceStreamResolver {
    data class Result(
        val status: StreamStatus,
        val inputStream: InputStream?,
        val audioInfo: AudioInfo,
        val startedAt: Double = 0.0,
    ) {
        enum class StreamStatus { FAILED, FINISHED, OK }
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
        positionProvider: () -> Double,
    ): Result {
        val info = source.resolveAudioInfo()
        val adjustedPosition = if (info.isLive) 0.0 else positionProvider()

        return when (source) {
            is HttpAudioSource, is ServerAudioSource -> {
                if (adjustedPosition > 0 && info.durationSeconds > 0 && adjustedPosition > info.durationSeconds) {
                    Result(Result.StreamStatus.FINISHED, null, info, adjustedPosition)
                } else {
                    val stream = FFmpegExecutor
                        .makeStream(info.audioUrl, info.sampleRate.toInt(), adjustedPosition, info.httpHeaders, info.isLive)
                        ?.first
                    Result(Result.StreamStatus.OK, stream, info, adjustedPosition)
                }
            }

            is StreamAudioSource -> {
                val stream = source.streamRetriever()
                if (adjustedPosition > 0 && !skipStreamToOffset(stream, adjustedPosition, info.sampleRate.toInt())) {
                    Result(Result.StreamStatus.FINISHED, null, info, adjustedPosition)
                } else {
                    Result(Result.StreamStatus.OK, stream, info, adjustedPosition)
                }
            }
        }
    }

    private suspend fun skipStreamToOffset(
        inputStream: InputStream,
        offsetSeconds: Double,
        effectiveSampleRate: Int,
    ): Boolean {
        val bytesPerSecond = effectiveSampleRate * 2L
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
