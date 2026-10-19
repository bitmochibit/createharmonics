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
            networkService.sendToPlayer(player, AudioServerInfoPacket(AudioUploadServer.port))
        }
    }

}


/**
 * Holder for determining the hostname and port for the audio server
 */
object AudioServerEndpoint {
    @Volatile var port: Int = 0
    fun baseUrl(): String {
        val host = Minecraft.getInstance().currentServer?.ip?.substringBefore(':') ?: "127.0.0.1"
        return "http://$host:$port"
    }
}
