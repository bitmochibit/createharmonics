package me.mochibit.createharmonics.audio.player

import me.mochibit.createharmonics.audio.info.AudioInfo
import me.mochibit.createharmonics.audio.source.AudioSource
import me.mochibit.createharmonics.audio.source.HttpAudioSource
import me.mochibit.createharmonics.audio.source.StreamAudioSource

object AudioSourceResolver {
    fun resolve(request: AudioRequest): AudioSource =
        when (request) {
            is AudioRequest.Stream -> {
                StreamAudioSource(request.streamRetriever, request.streamInfo)
            }

            is AudioRequest.Url -> {
                HttpAudioSource(request.url)
            }
        }
}
