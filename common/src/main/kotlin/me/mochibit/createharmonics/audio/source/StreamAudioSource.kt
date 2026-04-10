package me.mochibit.createharmonics.audio.source

import me.mochibit.createharmonics.audio.info.AudioInfo
import java.io.InputStream

class StreamAudioSource(
    val streamRetriever: () -> InputStream,
    val audioInfo: AudioInfo,
) : AudioSource {
    override suspend fun resolveAudioInfo(): AudioInfo = audioInfo
}
