package me.mochibit.createharmonics.foundation.inventory

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import org.jetbrains.annotations.Contract

/**
 * Represent a generic inventory with some operations
 *
 * This should be used with `contentService.createInventoryFromSpec` from [me.mochibit.createharmonics.foundation.services.ContentService] for accessing the main factory
 * which automatically creates an instance depending on the platform.
 */
interface GenericInventory {
    @Contract(pure = true) fun getSlots(): Int
    @Contract(pure = true) fun getStackInSlot(slot: Int): ItemStack
    @Contract(pure = true) fun getSlotLimit(slot: Int): Int
    @Contract(pure = true) fun isItemValid(slot: Int, stack: ItemStack): Boolean
    fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack
    fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack
}


interface MutableInventory : GenericInventory {
    fun setStackInSlot(slot: Int, stack: ItemStack)
}

interface PersistentInventory {
    fun serializeNBT(registries: HolderLookup.Provider): CompoundTag
    fun deserializeNBT(registries: HolderLookup.Provider, tag: CompoundTag)
}

interface StorageInventory : MutableInventory, PersistentInventory