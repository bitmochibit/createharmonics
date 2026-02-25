package me.mochibit.createharmonics.audio.cache

import me.mochibit.createharmonics.audio.process.YTdlpExecutor
import me.mochibit.createharmonics.foundation.err
import kotlin.time.Duration.Companion.minutes

/**
 * Generic cache for audio URLs and metadata extracted by yt-dlp.
 * Reduces redundant yt-dlp calls and speeds up audio processing for any supported URL.
 *
 * This cache works with YouTube, Soundcloud, and 1000+ other sites supported by yt-dlp.
 */
object AudioInfoCache {
    data class AudioInfo(
        val audioUrl: String,
        val durationSeconds: Int,
        val timestamp: Long = System.currentTimeMillis(),
        val title: String,
        val httpHeaders: Map<String, String> = emptyMap(),
    )

    private val cache = mutableMapOf<String, AudioInfo>()
    private val CACHE_TTL_MS = 5.minutes.inWholeMilliseconds
    private val ytdlpWrapper = YTdlpExecutor()

    /**
     * Get audio info from cache or extract it using yt-dlp.
     *
     * @param url The URL to extract audio info from (YouTube, Soundcloud, etc.)
     * @return AudioInfo if successful, null otherwise
     */
    suspend fun getAudioInfo(url: String): AudioInfo? {
        // Check cache first
        synchronized(cache) {
            cache[url]?.let { entry ->
                if (System.currentTimeMillis() - entry.timestamp < CACHE_TTL_MS) {
                    return entry
                } else {
                    cache.remove(url)
                }
            }
        }
        val extractedInfo = ytdlpWrapper.extractAudioInfo(url)

        if (extractedInfo != null) {
            val audioInfo =
                AudioInfo(
                    audioUrl = extractedInfo.audioUrl,
                    durationSeconds = extractedInfo.durationSeconds,
                    title = extractedInfo.title,
                    httpHeaders = extractedInfo.httpHeaders,
                )

            synchronized(cache) {
                cache[url] = audioInfo
            }
            return audioInfo
        } else {
            "AudioInfoCache: Failed to extract audio info for $url".err()
            return null
        }
    }

    /**
     * Invalidate a specific cache entry (useful when URL expires/fails).
     */
    fun invalidate(url: String) {
        synchronized(cache) {
            cache.remove(url)
        }
    }

    /**
     * Clear the entire cache.
     */
    fun clear() {
        synchronized(cache) {
            cache.clear()
        }
    }

    /**
     * Get the number of entries in the cache.
     */
    fun size(): Int = synchronized(cache) { cache.size }
}
