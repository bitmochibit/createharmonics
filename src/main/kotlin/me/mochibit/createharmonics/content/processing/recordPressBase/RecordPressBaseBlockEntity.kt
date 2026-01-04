package me.mochibit.createharmonics.content.processing.recordPressBase

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

    val currentUrlIndex: Int
        get() = behaviour.currentUrlIndex

    var urlTemplate: String
        get() = behaviour.audioUrls.firstOrNull() ?: ""
        set(value) {
            if (value.isNotEmpty()) {
                behaviour.audioUrls.clear()
                behaviour.audioUrls.add(value)
            }
            notifyUpdate()
        }

    var audioUrls: MutableList<String>
        get() = behaviour.audioUrls
        set(value) {
            behaviour.audioUrls = value
            notifyUpdate()
        }

    var randomMode: Boolean
        get() = behaviour.randomMode
        set(value) {
            behaviour.randomMode = value
            notifyUpdate()
        }
}
