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
    /**
     * Returns the slots
     *
     * @return the int
     */
    @Contract(pure = true) fun getSlots(): Int

    /**
     * Returns the stack in slot
     *
     * @param slot
     * @return the item stack
     */
    @Contract(pure = true) fun getStackInSlot(slot: Int): ItemStack

    /**
     * Returns the slot limit
     *
     * @param slot
     * @return the int
     */
    @Contract(pure = true) fun getSlotLimit(slot: Int): Int

    /**
     * Returns `true` if item valid
     *
     * @param slot
     * @param stack
     * @return `true` if the condition holds, `false` otherwise
     */
    @Contract(pure = true) fun isItemValid(slot: Int, stack: ItemStack): Boolean

    /**
     * Adds the item
     *
     * @param slot
     * @param stack
     * @param simulate
     * @return the item stack
     */
    fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack

    /**
     * Extract item
     *
     * @param slot
     * @param amount
     * @param simulate
     * @return the item stack
     */
    fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack
}


/**
 * Mutable inventory
 *
 */
interface MutableInventory : GenericInventory {
    /**
     * Sets the stack in slot
     *
     * @param slot
     * @param stack
     */
    fun setStackInSlot(slot: Int, stack: ItemStack)
}

/**
 * Persistent inventory
 *
 */
interface PersistentInventory {
    /**
     * Converts the nbt
     *
     * @param registries
     * @return the compound tag
     */
    fun serializeNBT(registries: HolderLookup.Provider): CompoundTag

    /**
     * Converts the nbt
     *
     * @param registries
     * @param tag
     */
    fun deserializeNBT(registries: HolderLookup.Provider, tag: CompoundTag)
}

/**
 * Storage inventory
 *
 */
interface StorageInventory : MutableInventory, PersistentInventory