package me.mochibit.createharmonics.audio.source

import me.mochibit.createharmonics.audio.cache.AudioInfoCache

/**
 * Generic audio source implementation for any yt-dlp compatible URL.
 *
 * Now uses caching to reduce redundant yt-dlp calls, just like YoutubeAudioSource.
 */
class YtdlpAudioSource(
    private val url: String,
) : AudioSource {
    private var cachedInfo: AudioInfoCache.AudioInfo? = null

    override fun getIdentifier(): String = url

    override suspend fun resolveAudioUrl(): String {
        if (cachedInfo == null) {
            cachedInfo = AudioInfoCache.getAudioInfo(url)
                ?: throw IllegalStateException("Failed to extract audio URL from: $url")
        }
        return cachedInfo!!.audioUrl
    }

    override suspend fun getDurationSeconds(): Int {
        if (cachedInfo == null) {
            cachedInfo = AudioInfoCache.getAudioInfo(url)
                ?: throw IllegalStateException("Failed to extract audio info from: $url")
        }
        return cachedInfo!!.durationSeconds
    }

    override suspend fun getAudioName(): String {
        if (cachedInfo == null) {
            cachedInfo = AudioInfoCache.getAudioInfo(url)
                ?: return "Unknown"
        }
        return cachedInfo!!.title
    }

    override suspend fun getHttpHeaders(): Map<String, String> {
        if (cachedInfo == null) {
            cachedInfo = AudioInfoCache.getAudioInfo(url)
                ?: return emptyMap()
        }
        return cachedInfo!!.httpHeaders
    }

    override fun getMetadata(): Map<String, Any> =
        mapOf(
            "source" to "ytdlp",
            "url" to url,
            "duration" to (cachedInfo?.durationSeconds ?: 0),
        )
}
