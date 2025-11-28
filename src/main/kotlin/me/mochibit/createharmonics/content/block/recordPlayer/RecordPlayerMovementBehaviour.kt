package me.mochibit.createharmonics.content.block.recordPlayer

import com.simibubi.create.api.behaviour.movement.MovementBehaviour
import com.simibubi.create.content.contraptions.behaviour.MovementContext
import com.simibubi.create.content.contraptions.render.ActorVisual
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import kotlinx.coroutines.Dispatchers
import me.mochibit.createharmonics.client.audio.AudioPlayer
import me.mochibit.createharmonics.client.audio.effect.EffectChain
import me.mochibit.createharmonics.client.audio.effect.LowPassFilterEffect
import me.mochibit.createharmonics.client.audio.effect.ReverbEffect
import me.mochibit.createharmonics.client.audio.effect.VolumeEffect
import me.mochibit.createharmonics.client.audio.effect.pitchShift.PitchFunction
import me.mochibit.createharmonics.client.audio.effect.pitchShift.PitchShiftEffect
import me.mochibit.createharmonics.client.audio.instance.MovingSoundInstance
import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerBlockEntity.Companion.MAX_PITCH
import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerBlockEntity.Companion.MIN_PITCH
import me.mochibit.createharmonics.content.item.EtherealRecordItem
import me.mochibit.createharmonics.content.item.EtherealRecordItem.Companion.getAudioUrl
import me.mochibit.createharmonics.coroutine.launchModCoroutine
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

    private fun getPlayerUUID(context: MovementContext): UUID {
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

    override fun stopMoving(context: MovementContext) {
        super.stopMoving(context)
        context.world.onClient {
            launchModCoroutine(Dispatchers.IO) {
                val tempData = context.temporaryData as TempData
                tempData.audioPlayer.stop()
                context.temporaryData = null
            }
        }
    }

    override fun tick(context: MovementContext) {
        super.tick(context)

        context.world.onClient {
            val tempData = context.temporaryData as TempData?
            val audioUrl = getAudioUrl(context)
            val isMoving = abs(context.animationSpeed) != 0f

            // Handle no URL case
            if (audioUrl == null) {
                if (tempData != null && tempData.audioPlayer.isPlaying) {
                    launchModCoroutine(Dispatchers.IO) {
                        stopClientPlayer(context)
                    }
                }
                return@onClient
            }

            // Handle paused state (not moving but has URL)
            if (!isMoving) {
                if (tempData != null && tempData.audioPlayer.isPlaying && !tempData.audioPlayer.isPaused) {
                    launchModCoroutine(Dispatchers.IO) {
                        pauseClientPlayer(context)
                    }
                }
                return@onClient
            }

            // Handle playing state - only start/resume if needed
            startClientPlayer(
                context, audioUrl, PitchFunction.smoothedRealTime(
                    sourcePitchFunction = PitchFunction.custom { _ ->
                        val currSpeed = abs(context.animationSpeed) / 10.0f
                        currSpeed.remapTo(0.0f, 700.0f, MIN_PITCH, MAX_PITCH)
                    },
                    transitionTimeSeconds = 0.5
                )
            )
        }
    }

    private fun startClientPlayer(context: MovementContext, audioUrl: String, pitchFunction: PitchFunction) {
        var tempData = context.temporaryData as TempData?
        if (tempData == null) {
            tempData = TempData(
                AudioPlayer(
                    { streamId, stream ->
                        MovingSoundInstance(
                            stream,
                            streamId,
                            posSupplier = {
                                BlockPos.containing(context.position)
                            },
                        )
                    },
                    getPlayerUUID(context).toString()
                ),
                lastAudioUrl = null
            )
            context.temporaryData = tempData
        }

        // Resume if paused
        if (tempData.audioPlayer.isPlaying && tempData.audioPlayer.isPaused) {
            launchModCoroutine(Dispatchers.IO) {
                tempData.audioPlayer.resume()
            }
            return
        }

        // Only start playing if not already playing or URL changed
        if (!tempData.audioPlayer.isPlaying || tempData.lastAudioUrl != audioUrl) {
            tempData.lastAudioUrl = audioUrl
            launchModCoroutine(Dispatchers.IO) {
                tempData.audioPlayer.play(
                    audioUrl,
                    EffectChain(
                        listOf(
                            PitchShiftEffect(pitchFunction),
                            VolumeEffect(0.8f),
                            LowPassFilterEffect(cutoffFrequency = 3000f),
                            ReverbEffect(roomSize = 0.5f, damping = 0.2f, wetMix = 0.8f)
                        )
                    ),
                )
            }
        }

    }

    private suspend fun pauseClientPlayer(context: MovementContext) {
        if (context.temporaryData is TempData) {
            (context.temporaryData as TempData).audioPlayer.pause()
        }
    }

    private suspend fun stopClientPlayer(context: MovementContext) {
        if (context.temporaryData is TempData) {
            (context.temporaryData as TempData).audioPlayer.stop()
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
        // Ensure inventory is synced from data to blockEntityData when structure is saved/synced
        if (context.data.contains("inventory")) {
            context.blockEntityData.put("inventory", context.data.getCompound("inventory"))
        }
    }

    data class TempData(
        val audioPlayer: AudioPlayer,
        var lastAudioUrl: String?
    )
}