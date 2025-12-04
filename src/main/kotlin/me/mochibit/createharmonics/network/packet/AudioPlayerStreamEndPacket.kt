package me.mochibit.createharmonics.network.packet

import com.simibubi.create.foundation.networking.SimplePacketBase
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent

class AudioPlayerStreamEndPacket(
    val audioPlayerId: String
) : SimplePacketBase() {
    constructor(buffer: FriendlyByteBuf) : this(
        audioPlayerId = buffer.readUtf()
    )

    override fun write(buffer: FriendlyByteBuf) {
        buffer.writeUtf(audioPlayerId)
    }

    override fun handle(context: NetworkEvent.Context): Boolean {
        context.enqueueWork {
            val blockEntity = me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerBlockEntity
                .getBlockEntityByPlayerUUID(audioPlayerId)
            blockEntity?.stopPlayer()
        }
        return true
    }
}