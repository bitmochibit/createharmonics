package me.mochibit.createharmonics.audio.info

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import me.mochibit.createharmonics.audio.process.FFprobeExecutor
import me.mochibit.createharmonics.audio.process.YTdlpExecutor
import me.mochibit.createharmonics.config.ClientConfig
import me.mochibit.createharmonics.foundation.debug
import java.net.URI
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes

data class AudioInfo(
    val audioUrl: String,
    val durationSeconds: Int,
    val title: String,
    val sampleRate: Float,
    val isLive: Boolean,
    val httpHeaders: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
) {
    companion object {
        suspend fun resolveUrl(rawUrl: String): AudioInfo = AudioInfoCache.getAudioInfo(rawUrl)
    }
}

object AudioInfoCache {
    private val cache = ConcurrentHashMap<String, AudioInfo>()
    private val CACHE_TTL_MS = 15.minutes.inWholeMilliseconds
    private val ytdlpWrapper: YTdlpExecutor by lazy { YTdlpExecutor() }
    private val ffprobeWrapper: FFprobeExecutor by lazy { FFprobeExecutor() }

    suspend fun getAudioInfo(url: String): AudioInfo {
        cache[url]?.let { entry ->
            if (!isUrlStillValid(entry.audioUrl)) {
                cache.remove(url)
            } else if (System.currentTimeMillis() - entry.timestamp >= CACHE_TTL_MS) {
                cache.remove(url)
            } else {
                return entry
            }
        }

        val extractedInfo = raceExtraction(url)
        return extractedInfo.also { cache[url] = it }
    }

    private suspend fun raceExtraction(rawUrl: String): AudioInfo =
        coroutineScope {
            var jobs: List<Deferred<Result<AudioInfo>>> =
                listOf(
                    async { runCatching { ffprobeWrapper.probe(rawUrl) } },
                    async { runCatching { ytdlpWrapper.extractAudioInfo(rawUrl) } },
                )

            var lastFailure: Throwable? = null

            while (jobs.isNotEmpty()) {
                val (finishedJob, result) =
                    select {
                        jobs.forEach { job -> job.onAwait { r -> job to r } }
                    }

                if (result.isSuccess) {
                    jobs.filter { it !== finishedJob }.forEach { it.cancel() }
                    return@coroutineScope result.getOrThrow()
                }

                lastFailure = result.exceptionOrNull()
                if (ClientConfig.debugAudioPlayer.get()) {
                    "[AUDIO PLAYER FAIL] extraction strategy failed for $rawUrl: ${lastFailure?.message ?: "no explicit cause"}"
                        .debug()
                }
                jobs = jobs.filter { it !== finishedJob }
            }

            throw lastFailure ?: IllegalStateException("Both extraction strategies failed for $rawUrl")
        }

    private fun isUrlStillValid(audioUrl: String): Boolean =
        try {
            val expire =
                URI
                    .create(audioUrl)
                    .query
                    .split("&")
                    .firstOrNull { it.startsWith("expire=") }
                    ?.substringAfter("expire=")
                    ?.toLong()
                    ?: return true
            Instant.ofEpochSecond(expire).isAfter(Instant.now())
        } catch (e: Exception) {
            true
        }

    fun clear() = cache.clear()

    fun size(): Int = cache.size
}