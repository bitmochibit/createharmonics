package me.mochibit.createharmonics.content.item

import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.audio.pcm.PitchFunction
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class EtherealDiscItem : Item(Properties().stacksTo(1)) {

    override fun use(pLevel: Level, pPlayer: Player, pUsedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        if (!pLevel.isClientSide) return InteractionResultHolder.pass(pPlayer.getItemInHand(pUsedHand))
        val itemStack = pPlayer.getItemInHand(pUsedHand)

        return InteractionResultHolder.sidedSuccess(itemStack, pLevel.isClientSide)
    }

    override fun getDescriptionId(): String {
        return "item.createharmonics.ethereal_disc"
    }
}

