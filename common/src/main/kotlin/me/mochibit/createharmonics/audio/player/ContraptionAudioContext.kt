package me.mochibit.createharmonics.audio.player

import com.simibubi.create.content.contraptions.behaviour.MovementContext
import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplier
import net.minecraft.world.level.Level
import org.joml.Vector3d

/**
 * Contraption audio context
 *
 * @constructor Creates a new ContraptionAudioContext
 * @property movementContext the movement context
 * @property volume
 * @property pitch
 * @property radius
 */
class ContraptionAudioContext(
    private val movementContext: MovementContext,
    private val volume: FloatSupplier,
    private val pitch: FloatSupplier,
    private val radius: FloatSupplier,
) : AudioSpatialContext {
    override fun mutatePosition(vec: Vector3d) {
        val p = movementContext.position ?: return
        vec.set(p.x, p.y, p.z)
    }

    override fun targetVolume(): Float = volume.getValue()

    override fun targetPitch(): Float = pitch.getValue()

    override fun targetRadius(): Float = radius.getValue()

    override fun level(): Level? = movementContext.world
}
