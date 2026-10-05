package me.mochibit.createharmonics.foundation.network.packet

import kotlinx.serialization.Serializable
import me.mochibit.createharmonics.audio.upload.AudioUploadServer
import me.mochibit.createharmonics.audio.upload.ClientAudioUpload
import me.mochibit.createharmonics.audio.upload.UploadTicketRegistry
import me.mochibit.createharmonics.config.ModConfigs
import me.mochibit.createharmonics.foundation.services.networkService
import net.minecraft.client.Minecraft

@AutoPacket
@Serializable
class RequestAudioUpload : C2SPacket {
    override fun handle(context: ServerPacketContext) {
        val player = context.player
        val name = player.gameProfile.name

        val denial = when {
            !AudioUploadServer.isRunning -> "Audio upload server is not running"
            !AudioUploadServer.hasFreeSlot(name) -> "You reached your audio file limit"
            else -> null
        }
        if (denial != null) {
            networkService.sendToPlayer(player, AudioUploadDeniedPacket(denial))
            return
        }

        val ticket = UploadTicketRegistry.issue(player.uuid, name)
        networkService.sendToPlayer(
            player,
            AudioUploadGrantedPacket(ticket.token, ModConfigs.server.audioServerPort.get(), expiresInSeconds = 60)
        )
    }
}

@AutoPacket
@Serializable
data class AudioUploadGrantedPacket(val token: String, val port: Int, val expiresInSeconds: Int) : S2CPacket {
    override fun handle(context: ClientPacketContext) = ClientAudioUpload.onGranted(this)
}

@AutoPacket
@Serializable
data class AudioUploadDeniedPacket(val reason: String) : S2CPacket {
    override fun handle(context: ClientPacketContext) = ClientAudioUpload.onDenied(reason)
}