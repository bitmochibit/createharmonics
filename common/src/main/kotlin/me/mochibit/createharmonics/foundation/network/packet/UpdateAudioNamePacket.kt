package me.mochibit.createharmonics.foundation.network.packet

import kotlinx.serialization.Serializable
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlockEntity
import me.mochibit.createharmonics.foundation.services.contentService

@Serializable
class UpdateAudioNamePacket(
    val audioPlayerId: String,
    val audioName: String,
) : ModPacket,
    C2SPacket {
    override fun handle(context: ModPacket.Context): Boolean {
        RecordPlayerBlockEntity.handleAudioTitleChange(audioPlayerId, audioName)
        return true
    }
}
