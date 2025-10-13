package me.mochibit.createharmonics.content.item


import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class EtherealDiscItem(
) : Item(Properties().stacksTo(1)) {
    override fun use(pLevel: Level, pPlayer: Player, pUsedHand: InteractionHand): InteractionResultHolder<ItemStack?> {

        return super.use(pLevel, pPlayer, pUsedHand)
    }
}