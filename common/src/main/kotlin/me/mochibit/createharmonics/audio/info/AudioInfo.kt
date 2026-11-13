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

/**
 * Audio info
 * Data class — a value holder; `equals`, `hashCode`, `toString` and `copy` are generated.
 *
 * @constructor Creates a new AudioInfo
 * @property audioUrl the audio url
 * @property durationSeconds the duration seconds
 * @property displayName the display name
 * @property sampleRate the sample rate
 * @property isLive the is live
 * @property httpHeaders the http headers
 * @property timestamp
 */
data class AudioInfo(
    val audioUrl: String,
    val durationSeconds: Int,
    val displayName: String,
    val sampleRate: Float,
    val isLive: Boolean,
    val httpHeaders: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
) {
    /**
     * Companion
     * Companion object — holds factory methods and constants for the enclosing class.
     *
     * @constructor Creates a new Companion
     */
    companion object {
        /**
         * Resolve url
         * Suspending function — must be called from a coroutine or another suspending function.
         *
         * @param rawUrl the raw url
         * @return the audio info
         */
        suspend fun resolveUrl(rawUrl: String): AudioInfo = AudioInfoCache.getAudioInfo(rawUrl)
    }
}


/**
 * Audio info cache
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new AudioInfoCache
 */
object AudioInfoCache {
    private val cache = ConcurrentHashMap<String, AudioInfo>()
    private val CACHE_TTL_MS = 15.minutes.inWholeMilliseconds

    /**
     * Returns the audio info
     * Suspending function — must be called from a coroutine or another suspending function.
     *
     * @param url
     * @return the audio info
     */
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
                    async { runCatching { FFprobeExecutor.probe(rawUrl) } },
                    async { runCatching { YTdlpExecutor.extractAudioInfo(rawUrl) } },
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

    /**
     * Removes
     *
     */
    fun clear() = cache.clear()

    /**
     * Size
     *
     * @return the int
     */
    fun size(): Int = cache.size
}