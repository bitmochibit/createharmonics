package me.mochibit.createharmonics.audio.player

import me.mochibit.createharmonics.audio.info.AudioInfo
import me.mochibit.createharmonics.audio.source.StreamAudioSource
import java.io.InputStream

sealed interface AudioRequest {
    fun isSameSource(other: AudioRequest): Boolean

    data class Url(
        val url: String,
    ) : AudioRequest {
        override fun isSameSource(other: AudioRequest): Boolean = other is Url && other.url == url
    }

    data class Stream(
        val streamRetriever: () -> InputStream,
        val streamInfo: AudioInfo,
    ) : AudioRequest {
        override fun isSameSource(other: AudioRequest): Boolean = other is Stream && other.streamInfo.title == streamInfo.title
    }
}
