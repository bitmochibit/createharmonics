package me.mochibit.createharmonics.foundation.network.packet

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import me.mochibit.createharmonics.audio.upload.*
import me.mochibit.createharmonics.audio.upload.client.AudioServerEndpoint
import me.mochibit.createharmonics.audio.upload.client.ClientAudioLibrary
import me.mochibit.createharmonics.audio.upload.client.ClientAudioUpload
import me.mochibit.createharmonics.content.uploader.AmethystCatalystBlockEntity
import me.mochibit.createharmonics.foundation.services.networkService
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

/**
 * Request to the server on what port the audio server is hosted to, and after received the port will be updated
 */
@AutoPacket
@Serializable
data class AudioServerInfoPacket(val port: Int, val host: String) : S2CPacket {
    override fun handle(context: ClientPacketContext) {
        AudioServerEndpoint.port = port
        AudioServerEndpoint.publicHost = host
    }
}

@AutoPacket
@Serializable
class RequestAudioUpload(
    @Contextual val blockPos: BlockPos,
    val fileName: String
) : C2SPacket {
    override fun handle(context: ServerPacketContext) {
        val player = context.player
        val name = player.gameProfile.name
        val level = player.level()
        val be = level.getBlockEntity(blockPos) as? AmethystCatalystBlockEntity

        if (be == null || player.distanceToSqr(Vec3.atCenterOf(blockPos)) > 64) {
            networkService.sendToPlayer(
                player,
                AudioUploadDeniedPacket("Amethyst Catalyst is not reachable or non existent")
            )
            return
        }

        if (!AudioUploadServer.isRunning) {
            networkService.sendToPlayer(player, AudioUploadDeniedPacket("Audio upload server is not running"))
            return
        }

        if (!AudioUploadServer.hasFreeSlot(name)) {
            networkService.sendToPlayer(player, AudioUploadDeniedPacket("You reached your audio file limit"))
            return
        }


        val session = be.tryReserve(fileName)
        if (session == null) {
            networkService.sendToPlayer(player, AudioUploadDeniedPacket("Amethyst Catalyst is already busy"))
            return
        }

        val ticket = UploadTicketRegistry.issue(player.uuid, name, session)
        networkService.sendToPlayer(
            player,
            AudioUploadGrantedPacket(ticket.token, expiresInSeconds = 60)
        )
    }
}

@AutoPacket
@Serializable
data class AudioUploadGrantedPacket(val token: String, val expiresInSeconds: Int) : S2CPacket {
    override fun handle(context: ClientPacketContext) = ClientAudioUpload.onGranted(this)
}

@AutoPacket
@Serializable
data class AudioUploadDeniedPacket(val reason: String) : S2CPacket {
    override fun handle(context: ClientPacketContext) = ClientAudioUpload.onDenied(reason)
}

// Actions

@AutoPacket
@Serializable
class RequestAudioList : C2SPacket {
    override fun handle(context: ServerPacketContext) {
        val player = context.player
        networkService.sendToPlayer(
            player, AudioListPacket(
                AudioUploadServer.list(player.gameProfile.name)
            )
        )
    }

}

@AutoPacket
@Serializable
class DeleteAudioPacket(
    @Contextual val blockPos: BlockPos,
    val fileId: String
) : C2SPacket {
    override fun handle(context: ServerPacketContext) {
        val player = context.player
        val name = player.gameProfile.name
        val be = player.level().getBlockEntity(blockPos) as? AmethystCatalystBlockEntity

        if (be == null || player.distanceToSqr(Vec3.atCenterOf(blockPos)) > 64) {
            networkService.sendToPlayer(player, AudioUploadDeniedPacket("Amethyst Catalyst is not reachable or non existent"))
            return
        }

        if (be.isBusy) {
            networkService.sendToPlayer(player, AudioUploadDeniedPacket("Amethyst Catalyst is already busy"))
            return
        }

        if (!AudioUploadServer.delete(name, fileId)) return

        be.tryStartDelete()
        networkService.sendToPlayer(player, AudioListPacket(AudioUploadServer.list(name)))
    }

}


@AutoPacket
@Serializable
class AudioListPacket(val entries: List<AudioMetadata>) : S2CPacket {
    override fun handle(context: ClientPacketContext) {
        ClientAudioLibrary.update(entries)
    }

}