package me.mochibit.createharmonics.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.mochibit.createharmonics.Logger.err
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.audio.provider.YTDL
import java.util.concurrent.TimeUnit

/**
 * Cache for YouTube audio URLs extracted by yt-dlp.
 * Reduces redundant yt-dlp calls and speeds up audio processing.
 */
object AudioUrlCache {
    private data class CacheEntry(
        val audioUrl: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val cache = mutableMapOf<String, CacheEntry>()
    private val CACHE_TTL_MS = TimeUnit.HOURS.toMillis(1) // 1 hour TTL

    /**
     * Get audio URL from cache or extract it using yt-dlp.
     */
    suspend fun getAudioUrl(youtubeUrl: String): String? {
        // Check cache first
        synchronized(cache) {
            cache[youtubeUrl]?.let { entry ->
                if (System.currentTimeMillis() - entry.timestamp < CACHE_TTL_MS) {
                    info("AudioUrlCache: Cache hit for $youtubeUrl")
                    return entry.audioUrl
                } else {
                    info("AudioUrlCache: Cache entry expired for $youtubeUrl")
                    cache.remove(youtubeUrl)
                }
            }
        }

        // Extract URL using yt-dlp
        info("AudioUrlCache: Extracting audio URL for $youtubeUrl")
        val audioUrl = extractAudioUrl(youtubeUrl)

        if (audioUrl != null) {
            synchronized(cache) {
                cache[youtubeUrl] = CacheEntry(audioUrl)
            }
            info("AudioUrlCache: Cached audio URL for $youtubeUrl")
        }

        return audioUrl
    }

    /**
     * Extract audio URL from YouTube using yt-dlp.
     */
    private suspend fun extractAudioUrl(youtubeUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val ytdlPath = YTDL.getExecutablePath() ?: return@withContext null

            val process = ProcessBuilder(
                ytdlPath,
                "-f", "ba/b",
                "--get-url",
                "--no-playlist",
                youtubeUrl
            ).start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                err("yt-dlp failed: $output")
                return@withContext null
            }

            output.trim().lines().firstOrNull { it.startsWith("http") }
        } catch (_: Exception) {
            err("Error extracting URL")
            null
        }
    }

    /**
     * Clear the cache.
     */
    fun clear() {
        synchronized(cache) {
            cache.clear()
        }
        info("AudioUrlCache: Cache cleared")
    }
}

