package me.mochibit.createharmonics.foundation.network.packet

import me.mochibit.createharmonics.foundation.shared.RecordPlayerHelper
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer

class AudioPlayerStreamEndPacket(
    val audioPlayerId: String,
    val failure: Boolean = false,
) : ModPacket {
    constructor(buffer: FriendlyByteBuf) : this(
        audioPlayerId = buffer.readUtf(),
        failure = buffer.readBoolean(),
    )

    override fun write(buffer: FriendlyByteBuf) {
        buffer.writeUtf(audioPlayerId)
        buffer.writeBoolean(failure)
    }

    override fun handle(player: ServerPlayer?): Boolean = RecordPlayerHelper.onStreamEnd(audioPlayerId, failure)
}
