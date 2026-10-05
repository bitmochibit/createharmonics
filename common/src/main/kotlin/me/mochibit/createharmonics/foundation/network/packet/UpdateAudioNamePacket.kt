package me.mochibit.createharmonics.foundation.network.packet

import kotlinx.serialization.Serializable
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlockEntity
import me.mochibit.createharmonics.foundation.services.contentService

@AutoPacket
@Serializable
class UpdateAudioNamePacket(
    val audioPlayerId: String,
    val audioName: String,
) : C2SPacket {
    override fun handle(context: ServerPacketContext) {
        RecordPlayerBlockEntity.handleAudioTitleChange(audioPlayerId, audioName)
    }
}
