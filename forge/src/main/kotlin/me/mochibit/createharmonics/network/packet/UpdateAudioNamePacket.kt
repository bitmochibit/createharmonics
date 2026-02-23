package me.mochibit.createharmonics.network.packet

import com.simibubi.create.foundation.networking.SimplePacketBase
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlockEntity
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent

class UpdateAudioNamePacket(
    val audioPlayerId: String,
    val audioName: String,
) : SimplePacketBase() {
    constructor(buffer: FriendlyByteBuf) : this(
        audioPlayerId = buffer.readUtf(),
        audioName = buffer.readUtf(),
    )

    override fun write(buffer: FriendlyByteBuf) {
        buffer.writeUtf(audioPlayerId)
        buffer.writeUtf(audioName)
    }

    override fun handle(context: NetworkEvent.Context): Boolean {
        context.enqueueWork {
            RecordPlayerBlockEntity.handleAudioTitleChange(audioPlayerId, audioName)
        }
        return true
    }
}
