package me.mochibit.createharmonics.audio.player

import me.mochibit.createharmonics.audio.source.StreamAudioSource
import java.io.InputStream

sealed interface AudioRequest {
    data class Url(
        val url: String,
    ) : AudioRequest

    data class Stream(
        val streamRetriever: () -> InputStream,
        val streamInfo: StreamAudioSource.Information,
    ) : AudioRequest
}
