package me.mochibit.createharmonics.content.block.recordPlayer

import com.simibubi.create.api.behaviour.movement.MovementBehaviour
import com.simibubi.create.content.contraptions.behaviour.MovementContext
import com.simibubi.create.content.contraptions.render.ActorVisual
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.audio.AudioPlayer
import me.mochibit.createharmonics.audio.AudioPlayerRegistry
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.effect.pitchShift.PitchFunction
import me.mochibit.createharmonics.audio.effect.pitchShift.PitchShiftEffect
import me.mochibit.createharmonics.audio.instance.MovingSoundInstance
import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerBlockEntity.Companion.MAX_PITCH
import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerBlockEntity.Companion.MIN_PITCH
import me.mochibit.createharmonics.content.item.EtherealRecordItem
import me.mochibit.createharmonics.content.item.EtherealRecordItem.Companion.getAudioUrl
import me.mochibit.createharmonics.extension.onClient
import me.mochibit.createharmonics.extension.remapTo
import net.minecraft.core.BlockPos
import java.util.*
import kotlin.math.abs


/**
 * Handles Record Player behavior when attached to a moving contraption.
 * Uses block entity data (synced from server) to determine playback state.
 * Audio streams are managed by AudioPlayer's internal registry using the player block's UUID.
 */
class RecordPlayerMovementBehaviour : MovementBehaviour {


    override fun stopMoving(context: MovementContext) {
        super.stopMoving(context)
        context.world.onClient {
            val uuid = getPlayerUUID(context)
            if (uuid != null) {
                AudioPlayerRegistry.destroyPlayer(uuid.toString())
            }
        }
    }

    override fun tick(context: MovementContext) {
        super.tick(context)

        context.world.onClient {
            val uuid = getPlayerUUID(context) ?: return@onClient
            val playerId = uuid.toString()

            val audioUrl = getAudioUrl(context)
            val isMoving = abs(context.animationSpeed) != 0f

            // Get or create player for this contraption
            val player = AudioPlayerRegistry.getOrCreatePlayer(playerId) {
                Logger.info("Creating audio player for moving contraption: $playerId at ${context.localPos}")
                AudioPlayer(
                    { streamId, stream ->
                        MovingSoundInstance(
                            stream,
                            streamId,
                            posSupplier = { BlockPos.containing(context.position) },
                        )
                    },
                    playerId
                )
            }

            // Handle no URL case - stop playback
            if (audioUrl == null) {
                if (player.state != AudioPlayer.PlayState.STOPPED) {
                    player.stop()
                }
                return@onClient
            }

            // Handle paused state (not moving but has URL) - pause playback
            if (!isMoving) {
                if (player.state == AudioPlayer.PlayState.PLAYING) {
                    player.pause()
                }
                return@onClient
            }

            // Handle playing state - start or resume playback
            when (player.state) {
                AudioPlayer.PlayState.STOPPED -> {
                    // First time or after stop - start playing
                    Logger.info("Starting audio for player $playerId with URL: $audioUrl")
                    startClientPlayer(player, audioUrl, context)
                }

                AudioPlayer.PlayState.PAUSED -> {
                    // Resume from pause
                    Logger.info("Resuming audio for player $playerId")
                    player.resume()
                }

                AudioPlayer.PlayState.PLAYING -> {
                    // Already playing, do nothing
                }
            }
        }
    }

    override fun createVisual(
        visualizationContext: VisualizationContext,
        simulationWorld: VirtualRenderWorld,
        movementContext: MovementContext
    ): ActorVisual {
        return RecordPlayerActorVisual(visualizationContext, simulationWorld, movementContext)
    }

    override fun disableBlockEntityRendering(): Boolean {
        return true
    }

    override fun writeExtraData(context: MovementContext) {
        // This method syncs data from context.data to context.blockEntityData for client sync
        // For trains, we need to ensure playerUUID is available

        // Try to get UUID from context.data first
        if (context.data.contains("playerUUID")) {
            val uuid = context.data.getUUID("playerUUID")
            context.blockEntityData.putUUID("playerUUID", uuid)
            Logger.info("writeExtraData: Synced playerUUID $uuid from context.data at ${context.localPos}")
        } else {
            // If not in context.data, try to get it from the block entity
            val blockEntity = context.contraption.presentBlockEntities[context.localPos]
            if (blockEntity is RecordPlayerBlockEntity) {
                val uuid = blockEntity.playerUUID
                context.data.putUUID("playerUUID", uuid)
                context.blockEntityData.putUUID("playerUUID", uuid)
                Logger.info("writeExtraData: Retrieved and synced playerUUID $uuid from block entity at ${context.localPos}")
            } else {
                Logger.err("writeExtraData: Could not find playerUUID for ${context.localPos}")
            }
        }

        // Sync inventory
        if (context.data.contains("inventory")) {
            context.blockEntityData.put("inventory", context.data.getCompound("inventory"))
        }
    }

    private fun startClientPlayer(player: AudioPlayer, audioUrl: String, context: MovementContext) {
        val pitchFunction = PitchFunction.smoothedRealTime(
            sourcePitchFunction = PitchFunction.custom { _ ->
                val currSpeed = abs(context.animationSpeed) / 10.0f
                currSpeed.remapTo(0.0f, 700.0f, MIN_PITCH, MAX_PITCH)
            },
            transitionTimeSeconds = 0.5
        )

        player.play(
            audioUrl,
            EffectChain(
                listOf(
                    PitchShiftEffect(pitchFunction),
                )
            ),
        )

    }

    private fun getPlayerUUID(context: MovementContext): UUID? {
        if (!context.blockEntityData.contains("playerUUID")) {
            Logger.err("PlayerUUID not found in blockEntityData at ${context.localPos}")
            return null
        }
        return context.blockEntityData.getUUID("playerUUID")
    }

    /**
     * Get the audio URL from the record in the inventory (server-side data)
     */
    private fun getAudioUrl(context: MovementContext): String? {
        val inventory = getInventoryHandler(context) ?: return null
        val record = inventory.getStackInSlot(RecordPlayerBlockEntity.RECORD_SLOT)

        if (record.isEmpty || record.item !is EtherealRecordItem) {
            return null
        }

        return getAudioUrl(record)?.takeIf { it.isNotEmpty() }
    }

    private fun getInventoryHandler(context: MovementContext): RecordPlayerMountedStorage? {
        val storageManager = context.contraption.storage
        val rpInventory = storageManager.allItemStorages.get(context.localPos) as? RecordPlayerMountedStorage
        return rpInventory
    }
}