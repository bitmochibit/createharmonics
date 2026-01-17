package me.mochibit.createharmonics.content.processing.recordPressBase

import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlockEntity
import me.mochibit.createharmonics.registry.ModArmInteractionPoints
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

class RecordPressBaseArmInteractionPoint(
    type: ModArmInteractionPoints.RecordPressBaseType,
    level: Level,
    pos: BlockPos,
    state: BlockState,
) : ArmInteractionPoint(type, level, pos, state) {
    override fun extract(
        slot: Int,
        simulate: Boolean,
    ): ItemStack? {
        // Extract only from the outgoing slot (0 is held item, 1-8 are outgoing)
        if (slot == 0) {
            return ItemStack.EMPTY
        }

        return super.extract(slot, simulate)
    }

    override fun getInteractionPositionVector(): Vec3 =
        Vec3
            .atLowerCornerOf(pos)
            .add(.5, (14 / 16f).toDouble(), .5)
}
