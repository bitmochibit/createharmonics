package me.mochibit.createharmonics.foundation.network.packet

import kotlinx.serialization.Serializable
import me.mochibit.createharmonics.audio.player.ListenerEvent
import me.mochibit.createharmonics.audio.player.PlaybackSessions

@AutoPacket
@Serializable
class AudioPlayerListenerPacket(
    val audioPlayerId: String,
    val event: ListenerEvent,
) : C2SPacket {
    override fun handle(context: ServerPacketContext) {
        val session = PlaybackSessions.get(audioPlayerId) ?: return
        val sender = context.player.uuid
        when (event) {
            ListenerEvent.STARTED -> session.onStarted(sender)
            ListenerEvent.ENDED -> session.onEnded(sender)
            ListenerEvent.LEFT -> session.onLeft(sender)
            ListenerEvent.FAILED -> session.onFailed()
        }
    }
}