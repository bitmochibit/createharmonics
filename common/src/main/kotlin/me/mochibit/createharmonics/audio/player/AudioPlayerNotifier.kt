package me.mochibit.createharmonics.audio.player

import me.mochibit.createharmonics.foundation.network.packet.AudioPlayerStreamEndPacket
import me.mochibit.createharmonics.foundation.network.packet.UpdateAudioNamePacket
import me.mochibit.createharmonics.foundation.registry.ModPackets

class AudioPlayerNotifier(private val playerId: String) {
    fun audioTitleChanged(title: String) = ModPackets.sendToServer(UpdateAudioNamePacket(playerId, title))

    fun streamEnded() = ModPackets.sendToServer(AudioPlayerStreamEndPacket(playerId))
}