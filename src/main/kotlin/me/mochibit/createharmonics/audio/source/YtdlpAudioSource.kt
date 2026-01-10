package me.mochibit.createharmonics.audio.source

import me.mochibit.createharmonics.audio.process.YTdlpExecutor

/**
 * Generic audio source implementation for any yt-dlp compatible URL.
 * This includes SoundCloud, Spotify, Twitch, and hundreds of other sites.
 *
 * For YouTube URLs specifically, prefer using YoutubeAudioSource which has caching.
 * This source extracts info on-demand for maximum compatibility.
 */
class YtdlpAudioSource(
    private val url: String,
) : AudioSource {
    private val ytdlpExecutor = YTdlpExecutor()
    private var cachedInfo: YTdlpExecutor.AudioUrlInfo? = null

    override fun getIdentifier(): String = url

    override suspend fun resolveAudioUrl(): String {
        if (cachedInfo == null) {
            cachedInfo = ytdlpExecutor.extractAudioInfo(url)
                ?: throw IllegalStateException("Failed to extract audio URL from: $url")
        }
        return cachedInfo!!.audioUrl
    }

    override suspend fun getDurationSeconds(): Int {
        if (cachedInfo == null) {
            cachedInfo = ytdlpExecutor.extractAudioInfo(url)
                ?: throw IllegalStateException("Failed to extract audio info from: $url")
        }
        return cachedInfo!!.durationSeconds
    }

    override suspend fun getAudioName(): String {
        if (cachedInfo == null) {
            cachedInfo = ytdlpExecutor.extractAudioInfo(url)
                ?: return "Unknown"
        }
        return cachedInfo!!.title
    }

    override suspend fun getHttpHeaders(): Map<String, String> {
        if (cachedInfo == null) {
            cachedInfo = ytdlpExecutor.extractAudioInfo(url)
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
