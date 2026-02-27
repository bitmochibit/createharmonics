package me.mochibit.createharmonics.foundation.network.packet

import kotlinx.serialization.Serializable
import me.mochibit.createharmonics.audio.AudioPlayerRegistry

@Serializable
class AudioPlayerContextStopPacket(
    val audioPlayerId: String,
) : ModPacket,
    S2CPacket {
    override fun handle(context: ModPacket.Context): Boolean {
        AudioPlayerRegistry.destroyPlayer(audioPlayerId)
        return true
    }
}
