package me.mochibit.createharmonics.audio.source

import me.mochibit.createharmonics.audio.cache.YoutubeCache

/**
 * Audio source implementation for YouTube videos.
 */
class YoutubeAudioSource(
    private val youtubeUrl: String
) : AudioSource {

    private var cachedInfo: YoutubeCache.YoutubeAudioInfo? = null

    override fun getIdentifier(): String = youtubeUrl

    override suspend fun resolveAudioUrl(): String {
        if (cachedInfo == null) {
            cachedInfo = YoutubeCache.getAudioInfo(youtubeUrl)
                ?: throw IllegalStateException("Failed to extract audio URL from: $youtubeUrl")
        }
        return cachedInfo!!.audioUrl
    }

    override suspend fun getDurationSeconds(): Int {
        if (cachedInfo == null) {
            cachedInfo = YoutubeCache.getAudioInfo(youtubeUrl)
                ?: throw IllegalStateException("Failed to extract audio info from: $youtubeUrl")
        }
        return cachedInfo!!.durationSeconds
    }

    override fun getMetadata(): Map<String, Any> {
        return mapOf(
            "source" to "youtube",
            "url" to youtubeUrl,
            "duration" to (cachedInfo?.durationSeconds ?: 0)
        )
    }
}

