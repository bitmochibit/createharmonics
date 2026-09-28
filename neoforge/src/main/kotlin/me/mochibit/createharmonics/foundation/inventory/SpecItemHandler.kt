package me.mochibit.createharmonics.foundation.inventory

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.IItemHandler
import net.neoforged.neoforge.items.ItemStackHandler

class SpecItemHandler(
    private val spec: InventorySpec,
    var onChangeListener: GenericInventory.(slot: Int) -> Unit = {},
    var onLoadListener: GenericInventory.() -> Unit = {},
) : ItemStackHandler(spec.size), StorageInventory {

    private var loading = false

    override fun isItemValid(slot: Int, stack: ItemStack) = spec.isItemValid(slot, stack)
    override fun getSlotLimit(slot: Int) = spec.slotLimit(slot)
    override fun onContentsChanged(slot: Int) { if (!loading) onChangeListener(slot) }
    override fun onLoad() = onLoadListener()

    override fun serializeNBT(registries: HolderLookup.Provider): CompoundTag =
        spec.serializer.write(this, registries)

    override fun deserializeNBT(registries: HolderLookup.Provider, tag: CompoundTag) {
        loading = true
        try { spec.serializer.read(this, registries, tag) } finally { loading = false }
        onLoad()
    }

    companion object {
        fun copyOf(source: GenericInventory, spec: InventorySpec) = SpecItemHandler(spec).also { c ->
            for (i in 0 until source.getSlots()) c.stacks[i] = source.getStackInSlot(i).copy()
        }
    }
}

private class GenericInventoryAdapter(private val inv: GenericInventory) : IItemHandler {
    override fun getSlots() = inv.getSlots()
    override fun getStackInSlot(slot: Int) = inv.getStackInSlot(slot)
    override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean) = inv.insertItem(slot, stack, simulate)
    override fun extractItem(slot: Int, amount: Int, simulate: Boolean) = inv.extractItem(slot, amount, simulate)
    override fun getSlotLimit(slot: Int) = inv.getSlotLimit(slot)
    override fun isItemValid(slot: Int, stack: ItemStack) = inv.isItemValid(slot, stack)
}

fun GenericInventory.asItemHandler(): IItemHandler =
    this as? IItemHandler ?: GenericInventoryAdapter(this)