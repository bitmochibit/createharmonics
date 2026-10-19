package me.mochibit.createharmonics.audio.upload

import me.mochibit.createharmonics.foundation.eventbus.AutoHandler
import me.mochibit.createharmonics.foundation.eventbus.ModEventHandler
import me.mochibit.createharmonics.foundation.network.packet.AudioServerInfoPacket
import me.mochibit.createharmonics.foundation.services.eventService
import me.mochibit.createharmonics.foundation.services.networkService
import net.minecraft.client.Minecraft
import net.minecraft.server.level.ServerPlayer

@AutoHandler
object OnJoinSendAudioServerPort: ModEventHandler{
    override fun setupEvents() {
        eventService.onPlayerJoin { player ->
            if (player !is ServerPlayer) return@onPlayerJoin
            networkService.sendToPlayer(player, AudioServerInfoPacket(AudioUploadServer.port, AudioUploadServer.host))
        }
    }

}


/**
 * Holder for determining the hostname and port for the audio server
 */
object AudioServerEndpoint {
    @Volatile var port: Int = 0
    @Volatile var boundHost: String = "0.0.0.0"

    val effectiveHost get() = mapHost()
    fun baseUrl(): String {
        return "http://$effectiveHost:$port"
    }

    private fun mapHost():String {
        if (boundHost == "127.0.0.1" || boundHost == "localhost") {
            return "127.0.0.1"
        }

        return Minecraft.getInstance().currentServer?.ip?.substringBefore(':') ?: "127.0.0.1"
    }
}
