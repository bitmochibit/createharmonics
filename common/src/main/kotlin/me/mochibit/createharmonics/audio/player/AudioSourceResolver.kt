package me.mochibit.createharmonics.audio.player

import me.mochibit.createharmonics.audio.info.AudioInfo
import me.mochibit.createharmonics.audio.source.AudioSource
import me.mochibit.createharmonics.audio.source.HttpAudioSource
import me.mochibit.createharmonics.audio.source.ServerAudioSource
import me.mochibit.createharmonics.audio.source.StreamAudioSource
import me.mochibit.createharmonics.audio.upload.ServerAudioReference

/**
 * Audio source resolver
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new AudioSourceResolver
 */
object AudioSourceResolver {
    /**
     * Resolve
     *
     * @param request
     * @return the audio source
     */
    fun resolve(request: AudioRequest): AudioSource =
        when (request) {
            is AudioRequest.Stream -> {
                StreamAudioSource(request.streamRetriever, request.streamInfo)
            }

            is AudioRequest.Url ->
                ServerAudioReference.parse(request.url)?.let(::ServerAudioSource)
                    ?: HttpAudioSource(request.url)
        }
}
