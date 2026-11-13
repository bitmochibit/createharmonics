package me.mochibit.createharmonics.foundation.network.packet

import kotlinx.serialization.Serializable
import me.mochibit.createharmonics.audio.player.PlaybackSessions
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlockEntity
import me.mochibit.createharmonics.foundation.services.contentService

/**
 * Sets the audio name packet
 *
 * @constructor Creates a new UpdateAudioNamePacket
 * @property audioPlayerId the audio player id
 * @property audioName the audio name
 */
@AutoPacket
@Serializable
class UpdateAudioNamePacket(
    val audioPlayerId: String,
    val audioName: String,
) : C2SPacket {
    override fun handle(context: ServerPacketContext) {
        PlaybackSessions.get(audioPlayerId)?.owner?.onTitleChanged(audioName)
    }
}
