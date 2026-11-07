package me.mochibit.createharmonics.audio.player

import kotlinx.serialization.Serializable
import me.mochibit.createharmonics.foundation.network.packet.AudioPlayerListenerPacket
import me.mochibit.createharmonics.foundation.network.packet.UpdateAudioNamePacket
import me.mochibit.createharmonics.foundation.registry.ModPackets

@Serializable
enum class ListenerEvent { STARTED, ENDED, LEFT, FAILED }

class AudioPlayerNotifier(private val playerId: String) {
    fun audioTitleChanged(title: String) = ModPackets.sendToServer(UpdateAudioNamePacket(playerId, title))

    fun listenerStarted() = send(ListenerEvent.STARTED)
    fun streamEnded() = send(ListenerEvent.ENDED)
    fun listenerLeft() = send(ListenerEvent.LEFT)
    fun listenerFailed() = send(ListenerEvent.FAILED)

    private fun send(event: ListenerEvent) =
        ModPackets.sendToServer(AudioPlayerListenerPacket(playerId, event))
}