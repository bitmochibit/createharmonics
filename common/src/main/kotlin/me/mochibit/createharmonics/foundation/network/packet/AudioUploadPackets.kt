package me.mochibit.createharmonics.foundation.network.packet

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import me.mochibit.createharmonics.audio.upload.AudioMetadata
import me.mochibit.createharmonics.audio.upload.AudioUploadResponse
import me.mochibit.createharmonics.audio.upload.AudioUploadServer
import me.mochibit.createharmonics.audio.upload.UploadTicketRegistry
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
                AudioUploadDeniedPacket(AudioUploadResponse.NON_REACHABLE)
            )
            return
        }

        if (!AudioUploadServer.isRunning) {
            networkService.sendToPlayer(player, AudioUploadDeniedPacket(AudioUploadResponse.SERVER_NOT_RUNNING))
            return
        }

        if (!AudioUploadServer.hasFreeSlot(name)) {
            networkService.sendToPlayer(player, AudioUploadDeniedPacket(AudioUploadResponse.AUDIO_FILE_LIMIT))
            return
        }


        val session = be.tryReserve(fileName, player.uuid)
        if (session == null) {
            networkService.sendToPlayer(player, AudioUploadDeniedPacket(AudioUploadResponse.BLOCK_BUSY))
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
class CancelAudioUploadPacket(@Contextual val blockPos: BlockPos) : C2SPacket {
    override fun handle(context: ServerPacketContext) {
        val player = context.player
        (player.level().getBlockEntity(blockPos) as? AmethystCatalystBlockEntity)
            ?.cancelUpload(player.uuid)
    }
}

@AutoPacket
@Serializable
data class AudioUploadGrantedPacket(val token: String, val expiresInSeconds: Int) : S2CPacket {
    override fun handle(context: ClientPacketContext) = ClientAudioUpload.onGranted(this)
}

@AutoPacket
@Serializable
data class AudioUploadDeniedPacket(val reason: AudioUploadResponse) : S2CPacket {
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
            networkService.sendToPlayer(
                player,
                AudioUploadDeniedPacket(AudioUploadResponse.NON_REACHABLE)
            )
            return
        }

        if (be.isBusy) {
            networkService.sendToPlayer(player, AudioUploadDeniedPacket(AudioUploadResponse.BLOCK_BUSY))
            return
        }

        if (!AudioUploadServer.delete(name, fileId)) return

        be.tryStartDelete()
        networkService.sendToPlayer(player, AudioListPacket(AudioUploadServer.list(name)))
    }

}

@AutoPacket
@Serializable
class RenameAudioPacket(
    @Contextual val blockPos: BlockPos,
    val fileId: String,
    val newName: String?,
) : C2SPacket {
    override fun handle(context: ServerPacketContext) {
        val player = context.player
        val name = player.gameProfile.name
        val be = player.level().getBlockEntity(blockPos) as? AmethystCatalystBlockEntity

        if (be == null || player.distanceToSqr(Vec3.atCenterOf(blockPos)) > 64) {
            networkService.sendToPlayer(player, AudioUploadDeniedPacket(AudioUploadResponse.NON_REACHABLE))
            return
        }

        if (AudioUploadServer.rename(name, fileId, newName) == null) return
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