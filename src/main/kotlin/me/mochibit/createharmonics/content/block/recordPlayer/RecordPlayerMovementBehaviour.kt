package me.mochibit.createharmonics.content.block.recordPlayer

import com.simibubi.create.api.behaviour.movement.MovementBehaviour
import com.simibubi.create.api.contraption.storage.item.MountedItemStorage
import com.simibubi.create.content.contraptions.behaviour.MovementContext
import com.simibubi.create.content.contraptions.render.ActorVisual
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerItemHandler.Companion.MAIN_RECORD_SLOT
import me.mochibit.createharmonics.content.item.EtherealRecordItem
import me.mochibit.createharmonics.content.item.EtherealRecordItem.Companion.getAudioUrl
import me.mochibit.createharmonics.extension.onServer
import me.mochibit.createharmonics.network.ModNetworkHandler
import me.mochibit.createharmonics.network.packet.AudioPlayerContextStopPacket
import net.minecraftforge.network.PacketDistributor
import java.util.*

class RecordPlayerMovementBehaviour : MovementBehaviour {
    companion object {
        private const val PLAYBACK_STATE_KEY = "PlaybackState"
        private const val PLAY_TIME_KEY = "PlayTime"
        private const val PLAYER_UUID_KEY = "RecordPlayerUUID"
    }

    override fun stopMoving(context: MovementContext) {
        context.world.onServer {
            ModNetworkHandler.channel.send(
                PacketDistributor.ALL.noArg(),
                AudioPlayerContextStopPacket(getPlayerUUID(context).toString()),
            )
        }
    }

    override fun tick(context: MovementContext) {
    }

    override fun createVisual(
        visualizationContext: VisualizationContext,
        simulationWorld: VirtualRenderWorld,
        movementContext: MovementContext,
    ): ActorVisual = RecordPlayerActorVisual(visualizationContext, simulationWorld, movementContext)

    override fun disableBlockEntityRendering(): Boolean = true

    override fun writeExtraData(context: MovementContext) {
    }

    private fun getPlayerUUID(context: MovementContext): UUID? {
        // Read from context.data which is automatically synced between server and client
        if (!context.blockEntityData.contains(PLAYER_UUID_KEY)) {
            Logger.err("PlayerUUID not found at ${context.localPos}")
            return null
        }
        return context.blockEntityData.getUUID(PLAYER_UUID_KEY)
    }

    private fun getAudioUrl(context: MovementContext): String? {
        val inventory = getMountedStorage(context) ?: return null
        val record = inventory.getStackInSlot(MAIN_RECORD_SLOT)

        if (record.isEmpty || record.item !is EtherealRecordItem) {
            return null
        }

        return getAudioUrl(record)?.takeIf { it.isNotEmpty() }
    }

    private fun getMountedStorage(context: MovementContext): RecordPlayerMountedStorage? {
        val contraptionEntity = context.contraption.entity
        val storage: MountedItemStorage? =
            contraptionEntity
                .getContraption()
                .getStorage()
                .getAllItemStorages()
                .get(context.localPos)

        if (storage is RecordPlayerMountedStorage) {
            return storage
        }

        return null
    }
}
