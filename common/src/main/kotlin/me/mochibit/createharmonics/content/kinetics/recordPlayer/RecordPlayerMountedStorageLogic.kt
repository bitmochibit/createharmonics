package me.mochibit.createharmonics.content.kinetics.recordPlayer

import me.mochibit.createharmonics.content.records.EtherealRecordItem
import me.mochibit.createharmonics.foundation.inventory.GenericInventory
import me.mochibit.createharmonics.foundation.inventory.StorageInventory
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack

object RecordPlayerMountedStorageLogic {
    private val RANDOM = RandomSource.create()

    fun handleInteraction(inv: StorageInventory, player: ServerPlayer): Boolean {
        val slot = RecordPlayerBehaviour.RecordPlayerInventory.MAIN_RECORD_SLOT
        val itemInHand = player.mainHandItem
        val level = player.level() as ServerLevel
        val handItem = itemInHand.item

        if (handItem is EtherealRecordItem && inv.getStackInSlot(slot).isEmpty && !handItem.isRecordBroken()) {
            inv.setStackInSlot(slot, itemInHand.copy())
            itemInHand.shrink(1)
            level.playSound(null, player.blockPosition(), SoundEvents.ITEM_FRAME_ADD_ITEM,
                SoundSource.PLAYERS, 0.2f, 1f + RANDOM.nextFloat())
            return true
        }

        if (itemInHand.isEmpty && !inv.getStackInSlot(slot).isEmpty) {
            val disc = inv.getStackInSlot(slot).copy()
            inv.setStackInSlot(slot, ItemStack.EMPTY)
            if (!player.inventory.add(disc)) player.drop(disc, false)
            level.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP,
                SoundSource.PLAYERS, 0.2f, 1f + RANDOM.nextFloat())
            return true
        }
        return false
    }
}