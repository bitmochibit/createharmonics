package me.mochibit.createharmonics.content.block.recordPlayer

import com.simibubi.create.api.behaviour.movement.MovementBehaviour
import com.simibubi.create.content.contraptions.behaviour.MovementContext
import me.mochibit.createharmonics.audio.AudioPlayer
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.effect.LowPassFilterEffect
import me.mochibit.createharmonics.audio.effect.ReverbEffect
import me.mochibit.createharmonics.audio.effect.VolumeEffect
import me.mochibit.createharmonics.audio.effect.pitchShift.PitchFunction
import me.mochibit.createharmonics.audio.effect.pitchShift.PitchShiftEffect
import me.mochibit.createharmonics.audio.instance.StaticSoundInstance
import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerBlockEntity.Companion.MAX_PITCH
import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerBlockEntity.Companion.MIN_PITCH
import me.mochibit.createharmonics.extension.remapTo
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import net.minecraftforge.items.ItemStackHandler
import java.util.UUID
import kotlin.math.abs

class RecordPlayerMovementBehaviour: MovementBehaviour {
    data class PlayerTemporaryData(
        var currentPitch : Float = MIN_PITCH,
        val speedBasedPitchFunction: PitchFunction = PitchFunction.smoothedRealTime(
            sourcePitchFunction = PitchFunction.custom { _ -> currentPitch },
            transitionTimeSeconds = 0.5
        ),
        val playerUUID: UUID = UUID.randomUUID(),
        val playerPosition: Vec3 = Vec3.ZERO
    )


    fun getInventoryHandler(context: MovementContext): ItemStackHandler {
        val blockEntityData = context.blockEntityData
        return blockEntityData?.getCompound("inventory")?.let { nbt ->
            val handler = ItemStackHandler(1)
            handler.deserializeNBT(nbt)
            handler
        } ?: ItemStackHandler(1)
    }

    override fun startMoving(context: MovementContext) {
        super.startMoving(context)
        if (context.temporaryData == null)
            context.temporaryData = PlayerTemporaryData()

        val tempData = context.temporaryData as PlayerTemporaryData

        tempData.playerPosition.apply {
            x = context.position.x
            y = context.position.y
            z = context.position.z
        }

        AudioPlayer.play(
            "https://www.youtube.com/watch?v=rLeA7eQVIXk",
            soundInstanceProvider = { resLoc ->
                StaticSoundInstance(
                    resLoc,
                    this.worldPosition,
                    64,
                    1.0f
                )
            },
            EffectChain(
                listOf(
                    PitchShiftEffect(tempData.speedBasedPitchFunction),
                )
            ),
            streamId = tempData.playerUUID.toString()
        )
    }

    override fun visitNewPosition(
        context: MovementContext?,
        pos: BlockPos?
    ) {
        super.visitNewPosition(context, pos)
    }

    override fun onSpeedChanged(
        context: MovementContext?,
        oldMotion: Vec3?,
        motion: Vec3?
    ) {
        super.onSpeedChanged(context, oldMotion, motion)
        if (context == null || motion == null) return
        val tempData = context.temporaryData as? PlayerTemporaryData ?: return

        val speed = motion.length().toFloat()
        val newPitch = calculatePitch(speed)
        tempData.currentPitch = newPitch
    }

    override fun stopMoving(context: MovementContext?) {
        super.stopMoving(context)
    }

    private fun calculatePitch(speed: Float): Float {
        val currSpeed = abs(speed)
        return currSpeed.remapTo(16.0f, 256.0f, MIN_PITCH, MAX_PITCH)
    }

}