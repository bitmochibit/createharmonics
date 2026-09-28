package me.mochibit.createharmonics.foundation.inventory

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.world.item.ItemStack

interface InventorySerializer {
    fun write(inv: StorageInventory, registries: HolderLookup.Provider): CompoundTag
    fun read(inv: StorageInventory, registries: HolderLookup.Provider, tag: CompoundTag)
}

object ItemListSerializer : InventorySerializer {
    override fun write(inv: StorageInventory, registries: HolderLookup.Provider): CompoundTag {
        val list = ListTag()
        for (i in 0 until inv.getSlots()) {
            val stack = inv.getStackInSlot(i)
            if (stack.isEmpty) continue
            list.add(stack.save(registries, CompoundTag().apply { putInt("Slot", i) }))
        }
        return CompoundTag().apply {
            put("Items", list)
            putInt("Size", inv.getSlots())
        }
    }

    override fun read(inv: StorageInventory, registries: HolderLookup.Provider, tag: CompoundTag) {
        for (i in 0 until inv.getSlots()) inv.setStackInSlot(i, ItemStack.EMPTY)
        val list = tag.getList("Items", Tag.TAG_COMPOUND.toInt())
        for (i in list.indices) {
            val entry = list.getCompound(i)
            val slot = entry.getInt("Slot")
            if (slot in 0 until inv.getSlots()) {
                ItemStack.parse(registries, entry).ifPresent { inv.setStackInSlot(slot, it) }
            }
        }
    }
}