package me.mochibit.createharmonics.content.kinetics.recordPlayer

import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import me.mochibit.createharmonics.content.records.EtherealRecordItem
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.items.ItemStackHandler

abstract class RecordPlayerBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState,
) : KineticBlockEntity(type, pos, state) {
    private lateinit var behaviour: RecordPlayerBehaviour

    override fun addBehaviours(behaviours: MutableList<BlockEntityBehaviour>) {
        behaviour = RecordPlayerBehaviour(this)
        behaviours.add(behaviour)
    }

    override fun <T : Any?> getCapability(
        cap: Capability<T?>,
        side: Direction?,
    ): LazyOptional<T?> {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return behaviour.lazyItemHandler.cast()
        }
        return super.getCapability(cap, side)
    }

    fun applyInventoryToBlock(wrapped: ItemStackHandler) {
        for (i in 0 until itemHandler.slots) {
            itemHandler.setStackInSlot(i, if (i < wrapped.slots) wrapped.getStackInSlot(i) else ItemStack.EMPTY)
        }
    }

    val lazyItemHandler: LazyOptional<RecordPlayerItemHandler> = behaviour.lazyItemHandler
    val itemHandler: RecordPlayerItemHandler = behaviour.itemHandler

    val playbackState get() = behaviour.playbackState

    fun hasRecord(): Boolean = behaviour.hasRecord()

    fun insertRecord(discItem: ItemStack): Boolean = behaviour.insertRecord(discItem)

    fun popRecord(): ItemStack? = behaviour.popRecord()

    fun getRecord(): ItemStack = behaviour.getRecord()

    fun getRecordItem(): EtherealRecordItem? = behaviour.getRecordItem()

    fun startPlayer() = behaviour.startPlayer()

    fun stopPlayer() = behaviour.stopPlayer()

    fun pausePlayer() = behaviour.pausePlayer()

    fun setRecordItem(discItem: ItemStack) = behaviour.setRecord(discItem)
}
