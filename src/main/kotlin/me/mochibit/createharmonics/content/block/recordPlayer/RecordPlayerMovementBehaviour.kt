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
import me.mochibit.createharmonics.extension.onServer
import me.mochibit.createharmonics.extension.remapTo
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import net.minecraftforge.items.ItemStackHandler
import java.util.*


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
        val inventory = getInventoryHandler(context)
        val record = inventory.getStackInSlot(RecordPlayerBlockEntity.RECORD_SLOT)

        if (record.isEmpty || record.item !is EtherealRecordItem) {
            return null
        }

        return record.getAudioUrl()?.takeIf { it.isNotEmpty() }
    }

    private fun getInventoryHandler(context: MovementContext): ItemStackHandler {
        return context.blockEntityData?.getCompound("inventory")?.let { nbt ->
            val handler = ItemStackHandler(1)
            handler.deserializeNBT(nbt)
            handler
        } ?: ItemStackHandler(1)
    }


    override fun startMoving(context: MovementContext) {
        super.startMoving(context)
        context.world.onServer {
            context.data.putFloat("currentSpeed", 0f)
        }

        context.world.onClient {
            val audioUrl = getAudioUrl(context)
            if (audioUrl != null) {
                startClientPlayer(context, audioUrl)
            }
        }
    }

    /**
     * Called when contraption stops - cleanup audio
     */
    override fun stopMoving(context: MovementContext) {
        super.stopMoving(context)

        context.world.onClient {
            stopClientPlayer(context)
        }
    }


    override fun tick(context: MovementContext) {
        super.tick(context)

        // Use animationSpeed which is smoother than raw motion
        // animationSpeed is already smoothed by Create mod
        context.data?.putFloat("currentSpeed", context.animationSpeed)

        context.world.onClient {
            val audioUrl = getAudioUrl(context) ?: run {
                stopClientPlayer(context)
                return@onClient
            }
            // Only start if not already playing - play() will return null if already started
            startClientPlayer(context, audioUrl)
        }
    }

    override fun onSpeedChanged(
        context: MovementContext,
        oldMotion: Vec3,
        motion: Vec3
    ) {
        super.onSpeedChanged(context, oldMotion, motion)
        context.world.onServer {
            context.data.putFloat("currentSpeed", context.animationSpeed)
        }
    }

    private fun startClientPlayer(context: MovementContext, audioUrl: String) {
        AudioPlayer.play(
            audioUrl,
            listenerId = getPlayerUUID(context).toString(),
            soundInstanceProvider = { resLoc ->
                MovingSoundInstance(
                    resourceLocation = resLoc,
                    posSupplier = {
                        BlockPos.containing(context.position)
                    },
                    radius = 64
                )
            },
            effectChain = EffectChain(
                listOf(
                    PitchShiftEffect(
                        PitchFunction.smoothedRealTime(
                            sourcePitchFunction = PitchFunction.custom { _ ->
                                val speed = context.data?.getFloat("currentSpeed") ?: 0f
                                val pitch = speed.remapTo(
                                    0f,
                                    900f,
                                    MIN_PITCH,
                                    MAX_PITCH
                                )
                                pitch
                            },
                            transitionTimeSeconds = 0.5
                        )
                    ),
                    VolumeEffect(0.8f),
                    LowPassFilterEffect(cutoffFrequency = 3000f),
                    ReverbEffect(roomSize = 0.5f, damping = 0.2f, wetMix = 0.8f)
                )
            ),
        )
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


}