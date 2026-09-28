package me.mochibit.createharmonics.foundation.inventory

import net.minecraft.core.BlockPos
import net.minecraft.world.Containers
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * Attempts to insert the [stack] in the first available slot
 *
 * @return remainder stack that could not be inserted
 */
fun GenericInventory.insertAnywhere(stack: ItemStack, simulate: Boolean = false): ItemStack {
    var rest = stack
    if (rest.isEmpty) return rest
    for (i in 0 until getSlots()) {
        rest = insertItem(i, rest, simulate)
        if (rest.isEmpty) return ItemStack.EMPTY
    }
    return rest
}


/**
 * Attempts to insert [stack] first merging with compatible slots, then looks for empty slots
 *
 * @return remainder stack that could not be inserted
 */
fun GenericInventory.insertStacked(stack: ItemStack, simulate: Boolean = false): ItemStack {
    if (stack.isEmpty) return stack
    if (!stack.isStackable) return insertAnywhere(stack, simulate)

    var rest = stack
    for (i in 0 until getSlots()) {
        if (!ItemStack.isSameItemSameComponents(getStackInSlot(i), rest)) continue
        rest = insertItem(i, rest, simulate)
        if (rest.isEmpty) return ItemStack.EMPTY
    }
    for (i in 0 until getSlots()) {
        if (!getStackInSlot(i).isEmpty) continue
        rest = insertItem(i, rest, simulate)
        if (rest.isEmpty) return ItemStack.EMPTY
    }
    return rest
}

fun MutableInventory.clearContent() {
    for (slot in 0 until getSlots()) {
        this.setStackInSlot(slot, ItemStack.EMPTY)
    }
}


/**
 * Drop the inventory contents following the logic of [Containers.dropContents]
 */
fun GenericInventory.dropContents(level: Level, pos: BlockPos) {
    for (i in 0 until getSlots()) {
        Containers.dropItemStack(level, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), getStackInSlot(i))
    }
}

/**
 * Can the current stack [this] be stacked with [other]?
 */
fun ItemStack.canStackAmounts(other: ItemStack): Boolean =
    ItemStack.isSameItemSameComponents(this, other) && this.count + other.count <= this.maxStackSize