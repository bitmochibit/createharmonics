package me.mochibit.createharmonics.network.packet

import com.simibubi.create.foundation.networking.SimplePacketBase
import me.mochibit.createharmonics.audio.AudioPlayerRegistry
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent


class AudioPlayerContextStopPacket(
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
            AudioPlayerRegistry.destroyPlayer(audioPlayerId)
        }
        return true
    }
}


