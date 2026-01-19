package me.mochibit.createharmonics.network.packet

import com.simibubi.create.foundation.networking.SimplePacketBase
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlockEntity
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerMovementBehaviour
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent

class AudioPlayerStreamEndPacket(
    val audioPlayerId: String,
    val failure: Boolean = false,
) : SimplePacketBase() {
    constructor(buffer: FriendlyByteBuf) : this(
        audioPlayerId = buffer.readUtf(),
        failure = buffer.readBoolean(),
    )

    override fun write(buffer: FriendlyByteBuf) {
        buffer.writeUtf(audioPlayerId)
        buffer.writeBoolean(failure)
    }

    override fun handle(context: NetworkEvent.Context): Boolean {
        context.enqueueWork {
            RecordPlayerBlockEntity.handlePlaybackEnd(audioPlayerId, failure)

            // Handle moving record player (on contraptions)
            RecordPlayerMovementBehaviour.getContextByPlayerUUID(audioPlayerId)?.let { movementContext ->
                RecordPlayerMovementBehaviour.stopMovingPlayer(movementContext)
            }
                ?: me.mochibit.createharmonics.Logger.info(
                    "AudioPlayerStreamEndPacket: No moving player context found for player ID: $audioPlayerId",
                )
        }
        return true
    }
}
