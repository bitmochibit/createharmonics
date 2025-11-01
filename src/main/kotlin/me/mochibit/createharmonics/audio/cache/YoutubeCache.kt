package me.mochibit.createharmonics.audio.cache

import me.mochibit.createharmonics.Logger.err
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.audio.process.YTdlpExecutor
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

/**
 * Cache specifically for YouTube audio URLs and metadata extracted by yt-dlp.
 * Reduces redundant yt-dlp calls and speeds up YouTube audio processing.
 *
 * Note: This cache is YouTube-specific and should not be used for other audio sources.
 */
object YoutubeCache {
    data class YoutubeAudioInfo(
        val audioUrl: String,
        val durationSeconds: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val cache = mutableMapOf<String, YoutubeAudioInfo>()
    private val CACHE_TTL_MS = 2.minutes.inWholeMilliseconds
    private val ytdlpWrapper = YTdlpExecutor()

    /**
     * Get YouTube audio info from cache or extract it using yt-dlp.
     */
    suspend fun getAudioInfo(youtubeUrl: String): YoutubeAudioInfo? {
        // Check cache first
        synchronized(cache) {
            cache[youtubeUrl]?.let { entry ->
                if (System.currentTimeMillis() - entry.timestamp < CACHE_TTL_MS) {
                    info("YoutubeCache: Cache hit for $youtubeUrl")
                    return entry
                } else {
                    info("YoutubeCache: Cache entry expired for $youtubeUrl")
                    cache.remove(youtubeUrl)
                }
            }
        }

        // Extract URL and duration using yt-dlp
        info("YoutubeCache: Extracting audio info for $youtubeUrl")
        val extractedInfo = ytdlpWrapper.extractAudioInfo(youtubeUrl)

        if (extractedInfo != null) {
            val audioInfo = YoutubeAudioInfo(
                audioUrl = extractedInfo.audioUrl,
                durationSeconds = extractedInfo.durationSeconds
            )

            synchronized(cache) {
                cache[youtubeUrl] = audioInfo
            }
            info("YoutubeCache: Cached audio info for $youtubeUrl (duration: ${audioInfo.durationSeconds}s)")
            return audioInfo
        } else {
            err("YoutubeCache: Failed to extract audio info for $youtubeUrl")
            return null
        }
    }

    /**
     * Invalidate a specific cache entry (useful when URL expires/fails).
     */
    fun invalidate(youtubeUrl: String) {
        synchronized(cache) {
            if (cache.remove(youtubeUrl) != null) {
                info("YoutubeCache: Invalidated cache for $youtubeUrl")
            }
        }
    }

    /**
     * Clear the entire cache.
     */
    fun clear() {
        synchronized(cache) {
            cache.clear()
        }
        info("YoutubeCache: Cache cleared")
    }

    /**
     * Get the number of entries in the cache.
     */
    fun size(): Int = synchronized(cache) { cache.size }
}

