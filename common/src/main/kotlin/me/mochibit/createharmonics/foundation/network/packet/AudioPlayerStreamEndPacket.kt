package me.mochibit.createharmonics.foundation.network.packet

import com.simibubi.create.foundation.networking.SimplePacketBase
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlockEntity
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerMovementBehaviour
import me.mochibit.createharmonics.foundation.network.ModPacket
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

    override fun handle(player: ServerPlayer?): Boolean {
        RecordPlayerBlockEntity.handlePlaybackEnd(audioPlayerId, failure)

        RecordPlayerMovementBehaviour.getContextByPlayerUUID(audioPlayerId)?.let { movementContext ->
            RecordPlayerMovementBehaviour.stopMovingPlayer(movementContext)
        }
        return true
    }
}
