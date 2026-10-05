package me.mochibit.createharmonics.foundation.network.packet

import kotlinx.serialization.Serializable
import me.mochibit.createharmonics.content.kinetics.recordPlayer.GlobalRecordPlayerMovementBehaviourTracker
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlockEntity
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerMovementBehaviour

@AutoPacket
@Serializable
class AudioPlayerStreamEndPacket(
    val audioPlayerId: String,
    val failure: Boolean = false,
) :
    C2SPacket {
    override fun handle(context: ServerPacketContext) {
        RecordPlayerBlockEntity.handlePlaybackEnd(audioPlayerId, failure)
        GlobalRecordPlayerMovementBehaviourTracker.canRestart += audioPlayerId
    }
}
