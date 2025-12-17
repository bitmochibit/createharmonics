package me.mochibit.createharmonics.network.packet

import com.simibubi.create.foundation.networking.SimplePacketBase
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBehaviour
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerMovementBehaviour
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent

class AudioPlayerStreamEndPacket(
    val audioPlayerId: String,
) : SimplePacketBase() {
    constructor(buffer: FriendlyByteBuf) : this(
        audioPlayerId = buffer.readUtf(),
    )

    override fun write(buffer: FriendlyByteBuf) {
        buffer.writeUtf(audioPlayerId)
    }

    override fun handle(context: NetworkEvent.Context): Boolean {
        context.enqueueWork {
            val blockEntity =
                RecordPlayerBehaviour
                    .getBlockEntityByPlayerUUID(audioPlayerId)
            if (blockEntity != null) {
                blockEntity.stopPlayer()
            } else {
                me.mochibit.createharmonics.Logger.info(
                    "AudioPlayerStreamEndPacket: No static block entity found for player ID: $audioPlayerId",
                )
            }

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
