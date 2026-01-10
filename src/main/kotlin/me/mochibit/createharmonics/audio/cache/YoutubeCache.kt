package me.mochibit.createharmonics.audio.cache

/**
 * Cache specifically for YouTube audio URLs and metadata extracted by yt-dlp.
 * Reduces redundant yt-dlp calls and speeds up YouTube audio processing.
 *
 * @deprecated Use AudioInfoCache instead, which supports all yt-dlp compatible URLs.
 * This class is now a wrapper around AudioInfoCache for backward compatibility.
 */
@Deprecated(
    "Use AudioInfoCache instead",
    ReplaceWith("AudioInfoCache", "me.mochibit.createharmonics.audio.cache.AudioInfoCache"),
)
object YoutubeCache {
    data class YoutubeAudioInfo(
        val audioUrl: String,
        val durationSeconds: Int,
        val timestamp: Long = System.currentTimeMillis(),
        val title: String,
        val httpHeaders: Map<String, String> = emptyMap(),
    )

    /**
     * Get YouTube audio info from cache or extract it using yt-dlp.
     * This now delegates to AudioInfoCache for unified caching.
     */
    suspend fun getAudioInfo(youtubeUrl: String): YoutubeAudioInfo? {
        val audioInfo = AudioInfoCache.getAudioInfo(youtubeUrl) ?: return null
        return YoutubeAudioInfo(
            audioUrl = audioInfo.audioUrl,
            durationSeconds = audioInfo.durationSeconds,
            timestamp = audioInfo.timestamp,
            title = audioInfo.title,
            httpHeaders = audioInfo.httpHeaders,
        )
    }

    /**
     * Invalidate a specific cache entry (useful when URL expires/fails).
     */
    fun invalidate(youtubeUrl: String) {
        AudioInfoCache.invalidate(youtubeUrl)
    }

    /**
     * Clear the entire cache.
     */
    fun clear() {
        AudioInfoCache.clear()
    }

    /**
     * Get the number of entries in the cache.
     */
    fun size(): Int = AudioInfoCache.size()
}
