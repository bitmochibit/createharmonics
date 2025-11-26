package me.mochibit.createharmonics.content.block.recordPlayer

import com.simibubi.create.api.behaviour.movement.MovementBehaviour
import com.simibubi.create.content.contraptions.behaviour.MovementContext
import com.simibubi.create.content.contraptions.render.ActorVisual
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import me.mochibit.createharmonics.audio.AudioPlayer
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.effect.LowPassFilterEffect
import me.mochibit.createharmonics.audio.effect.ReverbEffect
import me.mochibit.createharmonics.audio.effect.VolumeEffect
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
            context.temporaryData = null
            stopClientPlayer(context)
        }
    }

    override fun tick(context: MovementContext) {
        super.tick(context)

        context.world.onClient {

            val audioUrl = getAudioUrl(context)

            if (audioUrl == null) {
                stopClientPlayer(context)
                return@onClient
            }

            if (abs(context.animationSpeed) == 0f) {
                pauseClientPlayer(context)
                return@onClient
            }

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
        if (AudioPlayer.isPlaying(getPlayerUUID(context).toString())) {
            resumeClientPlayer(context)
            return
        }
        AudioPlayer.play(
            audioUrl,
            listenerId = getPlayerUUID(context).toString(),
            soundInstanceProvider = { streamId, stream ->
                MovingSoundInstance(
                    stream,
                    streamId,
                    posSupplier = {
                        BlockPos.containing(context.position)
                    },
                )
            },
            effectChain = EffectChain(
                listOf(
                    PitchShiftEffect(pitchFunction),
                    VolumeEffect(0.8f),
                    LowPassFilterEffect(cutoffFrequency = 3000f),
                    ReverbEffect(roomSize = 0.5f, damping = 0.2f, wetMix = 0.8f)
                )
            ),
        )
    }

    private fun pauseClientPlayer(context: MovementContext) {
        AudioPlayer.pauseStream(getPlayerUUID(context).toString())
    }

    private fun resumeClientPlayer(context: MovementContext) {
        AudioPlayer.resumeStream(getPlayerUUID(context).toString())
    }

    private fun stopClientPlayer(context: MovementContext) {
        AudioPlayer.stopStream(getPlayerUUID(context).toString())
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
}