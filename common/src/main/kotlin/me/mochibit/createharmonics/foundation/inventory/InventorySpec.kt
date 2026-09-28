package me.mochibit.createharmonics.foundation.inventory

import net.minecraft.world.item.ItemStack

/**
 * Generic inventory specification used to create loader specific inventory handlers.
 */
data class InventorySpec(
    val size: Int,
    val isItemValid: (slot: Int, stack: ItemStack) -> Boolean = { _, _ -> true },
    val slotLimit: (slot: Int) -> Int = { 99 },
    val serializer: InventorySerializer = ItemListSerializer,
)