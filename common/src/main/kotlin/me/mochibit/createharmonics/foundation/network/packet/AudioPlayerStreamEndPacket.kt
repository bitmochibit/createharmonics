package me.mochibit.createharmonics.foundation.network.packet

import me.mochibit.createharmonics.foundation.services.contentService
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer

class AudioPlayerStreamEndPacket(
    val audioPlayerId: String,
    val failure: Boolean = false,
) : ModPacket {
    override fun handle(context: ModPacket.Context): Boolean {
        contentService.onStreamEnd(audioPlayerId, failure)
        return true
    }
}
