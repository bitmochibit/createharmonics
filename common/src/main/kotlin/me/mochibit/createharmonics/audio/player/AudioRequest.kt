package me.mochibit.createharmonics.audio.player

import me.mochibit.createharmonics.audio.info.AudioInfo
import me.mochibit.createharmonics.audio.source.StreamAudioSource
import java.io.InputStream

/**
 * Audio request
 * Sealed hierarchy — all subtypes are known at compile time, ideal for exhaustive `when`.
 *
 */
sealed interface AudioRequest {
    /**
     * Returns `true` if same source
     *
     * @param other
     * @return `true` if the condition holds, `false` otherwise
     */
    fun isSameSource(other: AudioRequest): Boolean

    /**
     * Url
     * Data class — a value holder; `equals`, `hashCode`, `toString` and `copy` are generated.
     *
     * @constructor Creates a new Url
     * @property url
     */
    data class Url(
        val url: String,
    ) : AudioRequest {
        override fun isSameSource(other: AudioRequest): Boolean = other is Url && other.url == url
    }

    /**
     * Stream
     * Data class — a value holder; `equals`, `hashCode`, `toString` and `copy` are generated.
     *
     * @constructor Creates a new Stream
     * @property streamRetriever the stream retriever
     * @property streamInfo the stream info
     */
    data class Stream(
        val streamRetriever: () -> InputStream,
        val streamInfo: AudioInfo,
    ) : AudioRequest {
        override fun isSameSource(other: AudioRequest): Boolean = other is Stream && other.streamInfo.displayName == streamInfo.displayName
    }
}
