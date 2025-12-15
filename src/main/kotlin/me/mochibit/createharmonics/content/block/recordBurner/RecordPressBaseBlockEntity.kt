package me.mochibit.createharmonics.content.block.recordBurner

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.util.LazyOptional

class RecordPressBaseBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState,
) : SmartBlockEntity(type, pos, state) {
    private lateinit var behaviour: RecordPressBaseBehaviour

    override fun addBehaviours(behaviours: MutableList<BlockEntityBehaviour>) {
        behaviour = RecordPressBaseBehaviour(this)
        behaviours.add(behaviour)
        behaviour.addSubBehaviours(behaviours)
    }

    override fun <T : Any?> getCapability(
        cap: Capability<T?>,
        side: Direction?,
    ): LazyOptional<T?> {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return behaviour.invProvider.cast()
        }
        return super.getCapability(cap, side)
    }
}
