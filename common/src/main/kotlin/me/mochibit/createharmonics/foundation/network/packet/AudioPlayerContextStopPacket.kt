package me.mochibit.createharmonics.foundation.network.packet

import kotlinx.serialization.Serializable
import me.mochibit.createharmonics.audio.AudioPlayerManager

/**
 * Audio player context stop packet
 *
 * @constructor Creates a new AudioPlayerContextStopPacket
 * @property audioPlayerId the audio player id
 */
@AutoPacket
@Serializable
class AudioPlayerContextStopPacket(
    val audioPlayerId: String,
) : S2CPacket {
    override fun handle(context: ClientPacketContext) {
        AudioPlayerManager.release(audioPlayerId)
    }
}
