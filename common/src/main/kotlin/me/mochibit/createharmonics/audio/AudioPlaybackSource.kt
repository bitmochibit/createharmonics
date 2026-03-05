package me.mochibit.createharmonics.audio

import java.io.InputStream

sealed class AudioPlaybackSource {
    data class FromUrl(
        val url: String,
    ) : AudioPlaybackSource() {
        init {
            require(url.isNotBlank()) { "URL cannot be blank" }
        }
    }

    data class FromStream(
        val stream: InputStream,
        val label: String = "stream",
        val sampleRateOverride: Int? = null,
    ) : AudioPlaybackSource()
}
