package me.mochibit.createharmonics.audio.upload

import kotlinx.serialization.Serializable
import me.mochibit.createharmonics.foundation.network.packet.RequestAudioList
import me.mochibit.createharmonics.foundation.services.networkService

@Serializable
data class AudioEntryInfo(
    val fileId: String,
    val extension: String,
    val title: String?,
    val artist: String?,
    val originalFileName: String?,
    val sizeBytes: Long,
) {
    val displayName get() = title ?: originalFileName ?: fileId

    companion object {
        fun from(m: AudioMetadata) = AudioEntryInfo(
            m.fileId, m.extension, m.title, m.artist,
            m.originalFileName,
            m.sizeBytes
        )
    }
}

object ClientAudioLibrary {
    @Volatile var entries: List<AudioEntryInfo> = emptyList()
        private set

    fun update(list: List<AudioEntryInfo>) {
        entries = list
    }

    fun refresh() = networkService.sendToServer(
        RequestAudioList()
    )
}