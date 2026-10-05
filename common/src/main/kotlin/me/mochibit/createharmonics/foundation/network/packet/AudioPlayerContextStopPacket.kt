package me.mochibit.createharmonics.foundation.network.packet

import kotlinx.serialization.Serializable
import me.mochibit.createharmonics.audio.AudioPlayerManager

@AutoPacket
@Serializable
class AudioPlayerContextStopPacket(
    val audioPlayerId: String,
) : S2CPacket {
    override fun handle(context: ClientPacketContext) {
        AudioPlayerManager.release(audioPlayerId)
    }
}
