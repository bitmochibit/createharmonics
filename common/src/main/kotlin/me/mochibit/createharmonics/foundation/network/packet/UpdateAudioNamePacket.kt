package me.mochibit.createharmonics.foundation.network.packet

import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlockEntity
import me.mochibit.createharmonics.foundation.network.ModPacket
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer

class UpdateAudioNamePacket(
    val audioPlayerId: String,
    val audioName: String,
) : ModPacket {
    constructor(buffer: FriendlyByteBuf) : this(
        audioPlayerId = buffer.readUtf(),
        audioName = buffer.readUtf(),
    )

    override fun write(buffer: FriendlyByteBuf) {
        buffer.writeUtf(audioPlayerId)
        buffer.writeUtf(audioName)
    }

    override fun handle(player: ServerPlayer?): Boolean {
        RecordPlayerBlockEntity.handleAudioTitleChange(audioPlayerId, audioName)
        return true
    }
}
