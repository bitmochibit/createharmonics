package me.mochibit.createharmonics.content.kinetics.recordPlayer

import me.mochibit.createharmonics.content.records.EtherealRecordItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.JukeboxBlock
import net.minecraftforge.items.ItemStackHandler

class RecordPlayerItemHandler(
    val behaviour: RecordPlayerBehaviour,
    slotCount: Int = 1,
) : ItemStackHandler(slotCount) {
    companion object {
        const val MAIN_RECORD_SLOT = 0
    }

    override fun onLoad() {
        behaviour.blockEntity.notifyUpdate()
    }

    override fun onContentsChanged(slot: Int) {
        super.onContentsChanged(slot)
        val hasDisc = !getStackInSlot(MAIN_RECORD_SLOT).isEmpty
        val be = behaviour.blockEntity
        be.level?.setBlockAndUpdate(
            be.blockPos,
            be.blockState.setValue(JukeboxBlock.HAS_RECORD, hasDisc),
        )
        be.notifyUpdate()
    }

    override fun isItemValid(
        slot: Int,
        stack: ItemStack,
    ): Boolean = stack.item is EtherealRecordItem || stack.isEmpty
}
