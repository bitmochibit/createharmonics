package me.mochibit.createharmonics.content.item

import me.mochibit.createharmonics.Config
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.audio.pcm.PitchFunction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class EtherealDiscItem(private val discType: Config.DiscType, props: Properties) : Item(props) {
    
    override fun use(pLevel: Level, pPlayer: Player, pUsedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        val itemStack = pPlayer.getItemInHand(pUsedHand)
        // logs for debugging
        info("=== Ethereal Disc Properties ===")
        info("Disc Type: ${discType.name}")
        info("Item: ${BuiltInRegistries.ITEM.getKey(this)}")
        info("Is Damageable: ${itemStack.isDamageableItem}")
        info("Max Damage: ${getMaxDamage(itemStack)}")
        info("Current Damage: ${itemStack.damageValue}")
        info("Remaining Uses: ${if (itemStack.isDamageableItem) getMaxDamage(itemStack) - itemStack.damageValue else "Infinite"}")
        info("Config Durability: ${Config.getDiscDurability(discType) ?: "Unbreakable"}")
        info("================================")
        
        if (!pLevel.isClientSide) return InteractionResultHolder.pass(itemStack)
        
        if (itemStack.isDamageableItem && !pLevel.isClientSide) {
            itemStack.hurtAndBreak(1, pPlayer) { player ->
                player.broadcastBreakEvent(pUsedHand)
            }
        }

        return InteractionResultHolder.sidedSuccess(itemStack, pLevel.isClientSide)
    }

    override fun getMaxDamage(stack: ItemStack): Int {
        // Use config value if available, otherwise fall back to item properties
        val configDurability = try {
            Config.getDiscDurability(discType)
        } catch (e: Exception) {
            null
        }
        return configDurability ?: super.getMaxDamage(stack)
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

