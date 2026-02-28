package me.mochibit.createharmonics.foundation.network.packet

import me.mochibit.createharmonics.foundation.services.contentService
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer

class UpdateAudioNamePacket(
    val audioPlayerId: String,
    val audioName: String,
) : ModPacket {
    override fun handle(context: ModPacket.Context): Boolean {
        contentService.onTitleChange(audioPlayerId, audioName)
        return true
    }
}
