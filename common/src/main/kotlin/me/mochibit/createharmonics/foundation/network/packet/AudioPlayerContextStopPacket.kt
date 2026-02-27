package me.mochibit.createharmonics.foundation.network.packet

import kotlinx.serialization.Serializable
import me.mochibit.createharmonics.audio.AudioPlayerRegistry
import me.mochibit.createharmonics.foundation.network.ModPacket
import me.mochibit.createharmonics.foundation.network.S2CPacket

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
