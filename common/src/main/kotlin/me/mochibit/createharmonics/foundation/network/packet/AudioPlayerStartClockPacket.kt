package me.mochibit.createharmonics.foundation.network.packet

import kotlinx.serialization.Serializable
import me.mochibit.createharmonics.content.kinetics.recordPlayer.GlobalRecordPlayerMovementBehaviourTracker
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlockEntity

@Serializable
class AudioPlayerStartClockPacket(
    val audioPlayerId: String,
) : ModPacket,
    C2SPacket {
    override fun handle(context: ModPacket.Context): Boolean {
        RecordPlayerBlockEntity.handlePlaytimeClockStart(audioPlayerId)

        GlobalRecordPlayerMovementBehaviourTracker.clockStarts += audioPlayerId
        return true
    }
}
