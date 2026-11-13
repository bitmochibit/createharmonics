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
 *
 * @constructor Creates a new AudioServerInfoPacket
 * @property port
 * @property host
 */
@AutoPacket
@Serializable
data class AudioServerInfoPacket(val port: Int, val host: String) : S2CPacket {
    override fun handle(context: ClientPacketContext) {
        AudioServerEndpoint.port = port
        AudioServerEndpoint.publicHost = host
    }
}

/**
 * Request audio upload
 *
 * @constructor Creates a new RequestAudioUpload
 * @property blockPos the block pos
 * @property fileName the file name
 */
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

/**
 * Stops the audio upload packet
 *
 * @constructor Creates a new CancelAudioUploadPacket
 * @property blockPos the block pos
 */
@AutoPacket
@Serializable
class CancelAudioUploadPacket(@Contextual val blockPos: BlockPos) : C2SPacket {
    override fun handle(context: ServerPacketContext) {
        val player = context.player
        (player.level().getBlockEntity(blockPos) as? AmethystCatalystBlockEntity)
            ?.cancelUpload(player.uuid)
    }
}

/**
 * Audio upload granted packet
 * Data class — a value holder; `equals`, `hashCode`, `toString` and `copy` are generated.
 *
 * @constructor Creates a new AudioUploadGrantedPacket
 * @property token
 * @property expiresInSeconds the expires in seconds
 */
@AutoPacket
@Serializable
data class AudioUploadGrantedPacket(val token: String, val expiresInSeconds: Int) : S2CPacket {
    override fun handle(context: ClientPacketContext) = ClientAudioUpload.onGranted(this)
}

/**
 * Audio upload denied packet
 * Data class — a value holder; `equals`, `hashCode`, `toString` and `copy` are generated.
 *
 * @constructor Creates a new AudioUploadDeniedPacket
 * @property reason
 */
@AutoPacket
@Serializable
data class AudioUploadDeniedPacket(val reason: AudioUploadResponse) : S2CPacket {
    override fun handle(context: ClientPacketContext) = ClientAudioUpload.onDenied(reason)
}

// Actions

/**
 * Request audio list
 *
 * @constructor Creates a new RequestAudioList
 */
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

/**
 * Removes the audio packet
 *
 * @constructor Creates a new DeleteAudioPacket
 * @property blockPos the block pos
 * @property fileId the file id
 */
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

        AudioUploadServer.delete(name, fileId)?.let { if (!it) return }

        be.tryStartDelete()
        networkService.sendToPlayer(player, AudioListPacket(AudioUploadServer.list(name)))
    }

}

/**
 * Rename audio packet
 *
 * @constructor Creates a new RenameAudioPacket
 * @property blockPos the block pos
 * @property fileId the file id
 * @property newName the new name
 */
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


/**
 * Audio list packet
 *
 * @constructor Creates a new AudioListPacket
 * @property entries
 */
@AutoPacket
@Serializable
class AudioListPacket(val entries: List<AudioMetadata>) : S2CPacket {
    override fun handle(context: ClientPacketContext) {
        ClientAudioLibrary.update(entries)
    }

}