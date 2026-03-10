package me.mochibit.createharmonics.audio.player

import me.mochibit.createharmonics.audio.source.AudioSource
import me.mochibit.createharmonics.audio.source.HttpAudioSource
import me.mochibit.createharmonics.audio.source.StreamAudioSource
import me.mochibit.createharmonics.audio.source.YtdlpAudioSource

object AudioSourceResolver {
    private val DIRECT_AUDIO_EXTENSIONS =
        setOf(
            "mp3",
            "mp4",
            "m4a",
            "m4b",
            "wav",
            "wave",
            "ogg",
            "oga",
            "opus",
            "flac",
            "aac",
            "webm",
            "wma",
            "aiff",
            "aif",
        )

    private fun isDirectAudioUrl(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#')
        return path.substringAfterLast('.', "").lowercase() in DIRECT_AUDIO_EXTENSIONS
    }

    fun resolve(request: AudioRequest): AudioSource =
        when (request) {
            is AudioRequest.Stream -> {
                StreamAudioSource(request.streamRetriever, request.streamInfo)
            }

            is AudioRequest.Url -> {
                if (isDirectAudioUrl(request.url)) {
                    HttpAudioSource(request.url)
                } else {
                    YtdlpAudioSource(request.url)
                }
            }
        }
}
