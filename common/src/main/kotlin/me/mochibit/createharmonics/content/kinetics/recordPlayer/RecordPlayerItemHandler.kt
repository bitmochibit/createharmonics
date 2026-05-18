package me.mochibit.createharmonics.content.kinetics.recordPlayer

import me.mochibit.createharmonics.content.records.EtherealRecordItem
import me.mochibit.createharmonics.foundation.extension.onServer
import net.minecraft.core.HolderLookup
import net.minecraft.core.NonNullList
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.ItemStackHandler

class RecordPlayerItemHandler(
    val behaviour: RecordPlayerBehaviour,
    private val targetSlotCount: Int = 2,
) : ItemStackHandler(targetSlotCount) {
    companion object {
        const val MAIN_RECORD_SLOT = 0
        const val RECORD_OUTPUT_SLOT = 1
    }

    override fun insertItem(
        slot: Int,
        stack: ItemStack,
        simulate: Boolean,
    ): ItemStack {
        if (slot == RECORD_OUTPUT_SLOT) return stack
        return super.insertItem(slot, stack, simulate)
    }

    override fun onLoad() {
        behaviour.be.onServer {
            behaviour.blockEntity.notifyUpdate()
        }
    }

    override fun onContentsChanged(slot: Int) {
        behaviour.be.onServer {
            val hasDisc = !getStackInSlot(MAIN_RECORD_SLOT).isEmpty
            val be = behaviour.blockEntity
            if (!hasDisc) {
                behaviour.onAudioTitleUpdate("")
            }
            be.level?.setBlockAndUpdate(
                be.blockPos,
                be.blockState.setValue(RecordPlayerTrait.HAS_ETHEREAL_RECORD, hasDisc),
            )
            be.notifyUpdate()
        }
    }

    override fun isItemValid(
        slot: Int,
        stack: ItemStack,
    ): Boolean {
        if (stack.isEmpty) return true
        val item = stack.item as? EtherealRecordItem ?: return false
        val validForSlot =
            when (slot) {
                MAIN_RECORD_SLOT -> {
                    !item.isRecordBroken()
                }

                else -> {
                    true
                }
            }
        return validForSlot
    }

    override fun deserializeNBT(
        provider: HolderLookup.Provider,
        nbt: CompoundTag,
    ) {
        super.deserializeNBT(provider, nbt)
        if (stacks.size < targetSlotCount) {
            val expanded = NonNullList.withSize(targetSlotCount, ItemStack.EMPTY)
            for (i in stacks.indices) expanded[i] = stacks[i]
            stacks = expanded
        }
    }
}
