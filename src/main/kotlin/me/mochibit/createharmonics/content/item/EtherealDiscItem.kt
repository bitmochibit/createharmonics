package me.mochibit.createharmonics.content.item

import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.audio.pcm.PitchFunction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class EtherealDiscItem(b: Boolean, props: Properties) : Item(Properties().stacksTo(1)) {

    override fun use(pLevel: Level, pPlayer: Player, pUsedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        if (!pLevel.isClientSide) return InteractionResultHolder.pass(pPlayer.getItemInHand(pUsedHand))
        val itemStack = pPlayer.getItemInHand(pUsedHand)

        return InteractionResultHolder.sidedSuccess(itemStack, pLevel.isClientSide)
    }

    override fun getDescriptionId(): String {
        return "item.${BuiltInRegistries.ITEM.getKey(this).namespace}.${BuiltInRegistries.ITEM.getKey(this).path}"
    }

    companion object {
        /**
         * Create a unique ResourceLocation based on YouTube URL and pitch function.
         * This allows the same URL with different pitch to be played simultaneously.
         */
        fun createResourceLocation(youtubeUrl: String, pitchFunction: PitchFunction): ResourceLocation {
            val hash = youtubeUrl.hashCode().toString(16)
            return ResourceLocation.fromNamespaceAndPath(
                CreateHarmonicsMod.MOD_ID,
                "youtube_$hash"
            )
        }
    }
}

