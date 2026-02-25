package me.mochibit.createharmonics.foundation.network.packet

import me.mochibit.createharmonics.audio.AudioPlayerRegistry
import me.mochibit.createharmonics.foundation.network.ModPacket
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer

class AudioPlayerContextStopPacket(
    val audioPlayerId: String,
) : ModPacket {
    constructor(buffer: FriendlyByteBuf) : this(
        audioPlayerId = buffer.readUtf(),
    )

    override fun write(buffer: FriendlyByteBuf) {
        buffer.writeUtf(audioPlayerId)
    }

    override fun handle(player: ServerPlayer?): Boolean {
        AudioPlayerRegistry.destroyPlayer(audioPlayerId)
        return true
    }
}
