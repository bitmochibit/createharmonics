package me.mochibit.createharmonics.foundation.inventory

import net.minecraft.world.item.ItemStack

/**
 * Generic inventory specification used to create loader specific inventory handlers.
 *
 * @constructor Creates a new InventorySpec
 * @property size
 * @property isItemValid the is item valid
 * @property slotLimit the slot limit
 * @property serializer
 * @property canExtract the can extract
 * @property canInsert the can insert
 */
data class InventorySpec(
    val size: Int,
    val isItemValid: (slot: Int, stack: ItemStack) -> Boolean = { _, _ -> true },
    val slotLimit: (slot: Int) -> Int = { 99 },
    val serializer: InventorySerializer = ItemListSerializer,
    val canExtract: (Int) -> Boolean = { true },
    val canInsert: (Int) -> Boolean = { true },
)