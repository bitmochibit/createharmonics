package me.mochibit.createharmonics.content.kinetics.recordPlayer

import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform
import dev.engine_room.flywheel.lib.transform.TransformStack
import net.createmod.catnip.math.AngleHelper
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.Vec3
import java.util.function.BiPredicate

class RecordPlayerValueBoxTransform(
    allowedDirections: BiPredicate<BlockState, Direction>,
) : CenteredSideValueBoxTransform(allowedDirections) {
    override fun getScale(): Float = 0.35f

    override fun getSouthLocation(): Vec3 = super.southLocation

    override fun getLocalOffset(
        level: LevelAccessor,
        pos: BlockPos,
        state: BlockState,
    ): Vec3 {
        val offset =
            super
                .getLocalOffset(level, pos, state)
                .add(Vec3.atLowerCornerOf(state.getValue(BlockStateProperties.FACING).normal).scale(2 / 5.35))

        val outwardOffset = Vec3.atLowerCornerOf(side.normal).scale(0.035)

        return offset.add(outwardOffset)
    }

    override fun rotate(
        level: LevelAccessor?,
        pos: BlockPos,
        state: BlockState,
        ms: PoseStack,
    ) {
        if (!side.axis.isHorizontal) {
            TransformStack
                .of(ms)
                .rotateYDegrees(AngleHelper.horizontalAngle(state.getValue(BlockStateProperties.FACING)) + 180)
        }
        super.rotate(level, pos, state, ms)
    }
}
