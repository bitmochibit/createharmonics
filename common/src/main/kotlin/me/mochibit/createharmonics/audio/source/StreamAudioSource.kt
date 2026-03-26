package me.mochibit.createharmonics.audio.source

import java.io.InputStream

class StreamAudioSource(
    val streamRetriever: () -> InputStream,
    val information: Information = Information(),
) : AudioSource {
    data class Information(
        val name: String = "Unknown",
        val bitrate: Int = 48_000,
        val duration: Int = 0,
    )

    override fun getIdentifier(): String = information.name

    override suspend fun resolveAudioUrl(): String = ""

    override suspend fun getDurationSeconds(): Int = information.duration

    override suspend fun getSampleRate(): Int = information.bitrate

    override suspend fun getAudioName(): String = information.name

    override suspend fun isLive(): Boolean = false
}
