package me.mochibit.createharmonics.audio.source

import me.mochibit.createharmonics.audio.info.AudioInfo
import me.mochibit.createharmonics.audio.info.AudioInfoCache

/**
 * Generic audio source implementation for any yt-dlp compatible URL.
 */
class YtdlpAudioSource(
    private val url: String,
) : AudioSource {
    override suspend fun resolveAudioInfo(): AudioInfo = AudioInfo.withYtdlp(url)
}
