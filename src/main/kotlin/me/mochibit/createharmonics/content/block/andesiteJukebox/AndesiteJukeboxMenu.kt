package me.mochibit.createharmonics.content.block.andesiteJukebox

import me.mochibit.createharmonics.registry.ModMenuTypesRegistry
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraftforge.items.SlotItemHandler

class AndesiteJukeboxMenu : AbstractContainerMenu {
    private val blockEntity: AndesiteJukeboxBlockEntity

    // Constructor for server-side
    constructor(id: Int, playerInventory: Inventory, blockEntity: AndesiteJukeboxBlockEntity) : super(
        ModMenuTypesRegistry.ANDESITE_JUKEBOX.get(),
        id
    ) {
        this.blockEntity = blockEntity

        // Add the disc slot (centered)
        addSlot(SlotItemHandler(blockEntity.inventory, 0, 80, 35))

        // Add player inventory
        for (row in 0..2) {
            for (col in 0..8) {
                addSlot(Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18))
            }
        }

        // Add player hotbar
        for (col in 0..8) {
            addSlot(Slot(playerInventory, col, 8 + col * 18, 142))
        }
    }

    // Constructor for client-side (from packet)
    constructor(id: Int, playerInventory: Inventory, buf: FriendlyByteBuf) : this(
        id,
        playerInventory,
        playerInventory.player.level().getBlockEntity(buf.readBlockPos()) as AndesiteJukeboxBlockEntity
    )

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        var itemStack = ItemStack.EMPTY
        val slot = slots[index]

        if (slot.hasItem()) {
            val slotStack = slot.item
            itemStack = slotStack.copy()

            if (index == 0) {
                // Moving from jukebox slot to player inventory
                if (!moveItemStackTo(slotStack, 1, slots.size, true)) {
                    return ItemStack.EMPTY
                }
            } else {
                // Moving from player inventory to jukebox slot
                if (!moveItemStackTo(slotStack, 0, 1, false)) {
                    return ItemStack.EMPTY
                }
            }

            if (slotStack.isEmpty) {
                slot.set(ItemStack.EMPTY)
            } else {
                slot.setChanged()
            }
        }

        return itemStack
    }

    override fun stillValid(player: Player): Boolean {
        return blockEntity.level?.let {
            it.getBlockEntity(blockEntity.blockPos) == blockEntity &&
                    player.distanceToSqr(
                        blockEntity.blockPos.x + 0.5,
                        blockEntity.blockPos.y + 0.5,
                        blockEntity.blockPos.z + 0.5
                    ) <= 64.0
        } ?: false
    }
}

