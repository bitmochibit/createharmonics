package me.mochibit.createharmonics.network.packet

import com.simibubi.create.foundation.networking.SimplePacketBase
import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerBehaviour
import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerMovementBehaviour
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
            // Handle static record player
            val blockEntity =
                RecordPlayerBehaviour
                    .getBlockEntityByPlayerUUID(audioPlayerId)
            blockEntity?.stopPlayer()

            // Handle moving record player (on contraptions)
            RecordPlayerMovementBehaviour.getContextByPlayerUUID(audioPlayerId)?.let { movementContext ->
                RecordPlayerMovementBehaviour.stopMovingPlayer(movementContext)
            }
        }
        return true
    }
}
