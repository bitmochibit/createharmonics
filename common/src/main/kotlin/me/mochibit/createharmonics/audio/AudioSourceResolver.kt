package me.mochibit.createharmonics.audio

import me.mochibit.createharmonics.audio.source.AudioSource
import me.mochibit.createharmonics.audio.source.HttpAudioSource
import me.mochibit.createharmonics.audio.source.YtdlpAudioSource

object AudioSourceResolver {
    private val DIRECT_EXTENSIONS =
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

    fun resolve(url: String): AudioSource = if (isDirect(url)) HttpAudioSource(url) else YtdlpAudioSource(url)

    fun isDirect(url: String): Boolean {
        val ext =
            url
                .substringBefore('?')
                .substringBefore('#')
                .substringAfterLast('.', "")
                .lowercase()
        return ext in DIRECT_EXTENSIONS
    }
}
