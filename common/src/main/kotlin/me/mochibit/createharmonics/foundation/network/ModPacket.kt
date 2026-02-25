package me.mochibit.createharmonics.foundation.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer

interface ModPacket {
    fun write(buffer: FriendlyByteBuf)

    fun handle(player: ServerPlayer?): Boolean
}
