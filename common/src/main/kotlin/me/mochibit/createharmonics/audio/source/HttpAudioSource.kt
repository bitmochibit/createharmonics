package me.mochibit.createharmonics.audio.source

import me.mochibit.createharmonics.audio.info.AudioInfo

/**
 * Audio source implementation for HTTP/HTTPS direct audio file URLs.
 *
 * Duration and title are resolved lazily via a single ffprobe call that reads
 * the container metadata without downloading any audio data.
 */
class HttpAudioSource(
    private val url: String,
) : AudioSource {
    override suspend fun resolveAudioInfo(): AudioInfo = AudioInfo.withFFProbe(url)
}
