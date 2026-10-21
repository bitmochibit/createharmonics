package me.mochibit.createharmonics.audio.upload.client

import me.mochibit.createharmonics.audio.upload.AudioMetadata
import me.mochibit.createharmonics.foundation.network.packet.RequestAudioList
import me.mochibit.createharmonics.foundation.services.networkService

/**
 * Local audio library of the uploaded audios on the server
 */
object ClientAudioLibrary {
    @Volatile var entries: List<AudioMetadata> = emptyList()
        private set

    fun update(list: List<AudioMetadata>) {
        entries = list
    }

    fun refresh() = networkService.sendToServer(
        RequestAudioList()
    )
}