package me.mochibit.createharmonics.audio.source

import me.mochibit.createharmonics.audio.info.AudioInfo
import java.io.InputStream

/**
 * Stream audio source
 *
 * @constructor Creates a new StreamAudioSource
 * @property streamRetriever the stream retriever
 * @property audioInfo the audio info
 */
class StreamAudioSource(
    val streamRetriever: () -> InputStream,
    val audioInfo: AudioInfo,
) : AudioSource {
    override suspend fun resolveAudioInfo(): AudioInfo = audioInfo
}


