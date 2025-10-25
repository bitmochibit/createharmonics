package me.mochibit.createharmonics.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.mochibit.createharmonics.Logger.err
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.audio.binProvider.YTDL
import java.util.concurrent.TimeUnit

/**
 * Cache for YouTube audio URLs extracted by yt-dlp.
 * Reduces redundant yt-dlp calls and speeds up audio processing.
 */
object AudioUrlCache {
    data class AudioInfo(
        val audioUrl: String,
        val durationSeconds: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val cache = mutableMapOf<String, AudioInfo>()
    private val CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(2) // 2 minutes TTL (URLs expire quickly)

    /**
     * Get audio info from cache or extract it using yt-dlp.
     */
    suspend fun getAudioInfo(youtubeUrl: String): AudioInfo? {
        // Check cache first
        synchronized(cache) {
            cache[youtubeUrl]?.let { entry ->
                if (System.currentTimeMillis() - entry.timestamp < CACHE_TTL_MS) {
                    info("AudioUrlCache: Cache hit for $youtubeUrl")
                    return entry
                } else {
                    info("AudioUrlCache: Cache entry expired for $youtubeUrl")
                    cache.remove(youtubeUrl)
                }
            }
        }

        // Extract URL and duration using yt-dlp
        info("AudioUrlCache: Extracting audio info for $youtubeUrl")
        val audioInfo = extractAudioInfo(youtubeUrl)

        if (audioInfo != null) {
            synchronized(cache) {
                cache[youtubeUrl] = audioInfo
            }
            info("AudioUrlCache: Cached audio info for $youtubeUrl (duration: ${audioInfo.durationSeconds}s)")
        }

        return audioInfo
    }

    /**
     * Extract audio URL and duration from YouTube using yt-dlp.
     */
    private suspend fun extractAudioInfo(youtubeUrl: String): AudioInfo? = withContext(Dispatchers.IO) {
        try {
            // Ensure yt-dlp is installed before using it
            if (!YTDL.isAvailable()) {
                info("AudioUrlCache: yt-dlp not found, installing...")
                if (!YTDL.install()) {
                    err("AudioUrlCache: Failed to install yt-dlp")
                    return@withContext null
                }
                info("AudioUrlCache: yt-dlp installed successfully")
            }

            val ytdlPath = YTDL.getExecutablePath()
            if (ytdlPath == null) {
                err("AudioUrlCache: yt-dlp executable not found even after installation")
                return@withContext null
            }

            // Get both URL and duration in one call
            // Use format selector to prefer direct URLs over HLS/DASH manifests
            // 'ba' = best audio, but we exclude HLS (m3u8) and DASH (mpd) manifests
            val process = ProcessBuilder(
                ytdlPath,
                "-f", "ba[protocol!*=m3u8][protocol!*=dash]/ba/b",
                "--get-url",
                "--get-duration",
                "--no-playlist",
                "--verbose",
                youtubeUrl
            ).start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val errorOutput = process.errorStream.bufferedReader().use { it.readText() }

            // Log yt-dlp output for debugging
            if (errorOutput.isNotBlank()) {
                errorOutput.lines().forEach { line ->
                    if (line.isNotBlank()) {
                        info("yt-dlp: $line")
                    }
                }
            }

            val exitCode = process.waitFor()

            if (exitCode != 0) {
                err("AudioUrlCache: yt-dlp failed with exit code $exitCode")
                return@withContext null
            }

            val lines = output.trim().lines()
            val audioUrl = lines.firstOrNull { it.startsWith("http") }
            val durationStr = lines.lastOrNull { it.contains(":") } // Format: HH:MM:SS or MM:SS

            if (audioUrl == null || durationStr == null) {
                err("AudioUrlCache: Failed to parse yt-dlp output. Lines: $lines")
                return@withContext null
            }

            val durationSeconds = parseDuration(durationStr)
            AudioInfo(audioUrl, durationSeconds)
        } catch (e: Exception) {
            err("AudioUrlCache: Error extracting audio info: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    private fun parseDuration(duration: String): Int {
        val parts = duration.split(":").map { it.toIntOrNull() ?: 0 }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2] // HH:MM:SS
            2 -> parts[0] * 60 + parts[1] // MM:SS
            1 -> parts[0] // SS
            else -> 0
        }
    }

    /**
     * Invalidate a specific cache entry (useful when URL expires/fails).
     */
    fun invalidate(youtubeUrl: String) {
        synchronized(cache) {
            if (cache.remove(youtubeUrl) != null) {
                info("AudioUrlCache: Invalidated cache for $youtubeUrl")
            }
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