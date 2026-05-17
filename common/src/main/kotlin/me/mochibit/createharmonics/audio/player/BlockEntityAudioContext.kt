package me.mochibit.createharmonics.audio.player

import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplier
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import org.joml.Vector3d

data class BlockEntityAudioContext(
    private val be: BlockEntity,
    private val volume: FloatSupplier,
    private val pitch: FloatSupplier,
    private val radius: FloatSupplier,
) : AudioSpatialContext {
    override fun mutatePosition(vec: Vector3d) {
        vec.set(
            be.blockPos.x.toDouble() + 0.5f,
            be.blockPos.y.toDouble() + 0.5f,
            be.blockPos.z.toDouble() + 0.5f,
        )
    }

    override fun targetVolume(): Float = volume.getValue()

    override fun targetPitch(): Float = pitch.getValue()

    override fun targetRadius(): Float = radius.getValue()

    override fun level(): Level? = be.level
}
