package me.mochibit.createharmonics.foundation.network.packet

import kotlinx.serialization.Serializable
import me.mochibit.createharmonics.audio.AudioPlayerManager

@Serializable
class AudioPlayerContextStopPacket(
    val audioPlayerId: String,
) : ModPacket,
    S2CPacket {
    override fun handle(context: ModPacket.Context): Boolean {
        AudioPlayerManager.release(audioPlayerId)
        return true
    }
}
