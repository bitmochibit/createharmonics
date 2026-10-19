package me.mochibit.createharmonics.content.processing.recordPressBase

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import me.mochibit.createharmonics.foundation.inventory.clearContent
import net.minecraft.core.BlockPos
import net.minecraft.world.Clearable
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

class RecordPressBaseBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState,
) : SmartBlockEntity(type, pos, state),
    Clearable {
    lateinit var behaviour: RecordPressBaseBehaviour
        private set

    override fun addBehaviours(behaviours: MutableList<BlockEntityBehaviour>) {
        behaviour = RecordPressBaseBehaviour(this)
        behaviours.add(behaviour)
        behaviour.addSubBehaviours(behaviours)
    }

    var configuration: RecordPressBaseConfiguration
        get() = behaviour.config
        set(value) {
            behaviour.config = value
            notifyUpdate()
        }

    override fun clearContent() {
        this.behaviour.itemHandler.clearContent()

        notifyUpdate()
    }
}
