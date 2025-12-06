package me.mochibit.createharmonics.content.block.recordPlayer

import com.simibubi.create.api.behaviour.movement.MovementBehaviour
import com.simibubi.create.content.contraptions.behaviour.MovementContext
import com.simibubi.create.content.contraptions.render.ActorVisual
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerBehaviour.PlaybackState
import me.mochibit.createharmonics.content.item.EtherealRecordItem
import me.mochibit.createharmonics.extension.onServer
import me.mochibit.createharmonics.network.ModNetworkHandler
import me.mochibit.createharmonics.network.packet.AudioPlayerContextStopPacket
import net.minecraft.world.item.ItemStack
import net.minecraftforge.network.PacketDistributor
import java.util.*
import kotlin.math.abs

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
        context.world.onServer {
            val currentSpeed = abs(context.animationSpeed)
            val hasRecord = hasRecord(context)
            val currentState = getPlaybackState(context)

            val newState =
                when {
                    !hasRecord -> {
                        PlaybackState.STOPPED
                    }

                    currentSpeed == 0f -> {
                        if (currentState == PlaybackState.PLAYING) {
                            PlaybackState.PAUSED
                        } else {
                            currentState
                        }
                    }

                    currentSpeed > 0f -> {
                        when (currentState) {
                            PlaybackState.PAUSED, PlaybackState.STOPPED -> PlaybackState.PLAYING
                            else -> currentState
                        }
                    }

                    else -> {
                        currentState
                    }
                }

            if (newState != currentState) {
                updatePlaybackState(context, newState)
            }
        }
    }

    private fun updatePlaybackState(
        context: MovementContext,
        newState: PlaybackState,
    ) {
        val oldState = getPlaybackState(context)

        context.blockEntityData.putString(PLAYBACK_STATE_KEY, newState.name)

        when {
            newState == PlaybackState.STOPPED -> {
                context.blockEntityData.putLong(PLAY_TIME_KEY, 0)
            }

            newState == PlaybackState.PLAYING && oldState != PlaybackState.PLAYING -> {
                context.blockEntityData.putLong(PLAY_TIME_KEY, System.currentTimeMillis())
            }
        }

        // Send to client this data, his block entity data will be synchronized
    }

    private fun getPlaybackState(context: MovementContext): PlaybackState {
        if (!context.blockEntityData.contains(PLAYBACK_STATE_KEY)) {
            return PlaybackState.STOPPED
        }
        return try {
            PlaybackState.valueOf(context.blockEntityData.getString(PLAYBACK_STATE_KEY))
        } catch (_: IllegalArgumentException) {
            PlaybackState.STOPPED
        }
    }

    private fun hasRecord(context: MovementContext): Boolean {
        val record = getRecordItem(context)
        return !record.isEmpty && record.item is EtherealRecordItem
    }

    override fun createVisual(
        visualizationContext: VisualizationContext,
        simulationWorld: VirtualRenderWorld,
        movementContext: MovementContext,
    ): ActorVisual = RecordPlayerActorVisual(visualizationContext, simulationWorld, movementContext)

    override fun disableBlockEntityRendering(): Boolean = true

    private fun getPlayerUUID(context: MovementContext): UUID? {
        if (!context.blockEntityData.contains(PLAYER_UUID_KEY)) {
            Logger.err("PlayerUUID not found at ${context.localPos}")
            return null
        }
        return context.blockEntityData.getUUID(PLAYER_UUID_KEY)
    }

    private fun getRecordItem(context: MovementContext): ItemStack {
        val handler =
            context.contraption.storage.allItemStorages
                .get(context.localPos) as? RecordPlayerMountedStorage ?: return ItemStack.EMPTY
        return handler.getRecord()
    }
}
