package me.mochibit.createharmonics.network.packet

import com.simibubi.create.foundation.networking.SimplePacketBase
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent

/**
 * Packet to request to join an AudioPlayer lobby, managed by [AudioPlayerLobbyRegistry][me.mochibit.createharmonics.audio.server.AudioPlayerLobbyRegistry].
 * The lobby automatically starts when the first player joins, establishing the time reference for synchronized playback.
 * Subsequent players joining the same lobby will receive the current playback offset to synchronize with existing players.
 */
class AudioPlayerLobbyJoinPacket(
    val clientId: String,
    val audioPlayerId: String
) : SimplePacketBase() {
    constructor(buffer: FriendlyByteBuf) : this(
        clientId = buffer.readUtf(),
        audioPlayerId = buffer.readUtf()
    )

    override fun write(buffer: FriendlyByteBuf) {
        buffer.writeUtf(clientId)
        buffer.writeUtf(audioPlayerId)
    }

    override fun handle(context: NetworkEvent.Context): Boolean {
        context.enqueueWork {

        }
        return true
    }
}


