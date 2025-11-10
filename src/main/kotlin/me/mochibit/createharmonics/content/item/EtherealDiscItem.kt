package me.mochibit.createharmonics.content.item

import me.mochibit.createharmonics.Config
import me.mochibit.createharmonics.Logger.info
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraftforge.registries.ForgeRegistries

class EtherealRecordItem(private val recordType: Config.RecordType, props: Properties) : Item(props) {
    companion object {
        const val AUDIO_URL_TAG_KEY = "audio_url"

        fun getAudioUrl(stack: ItemStack): String? {
            if (stack.item !is EtherealRecordItem) return null
            return stack.tag?.getString(AUDIO_URL_TAG_KEY)
        }

        fun setAudioUrl(stack: ItemStack, url: String) {
            if (stack.item !is EtherealRecordItem) return

            if (stack.tag == null) {
                stack.tag = net.minecraft.nbt.CompoundTag()
            }
            stack.tag?.putString(AUDIO_URL_TAG_KEY, url)
        }
    }

    override fun use(pLevel: Level, pPlayer: Player, pUsedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        val itemStack = pPlayer.getItemInHand(pUsedHand)

        // Debug logs
        if (pLevel.isClientSide) {
            info("=== Ethereal Record Properties ===")
            info("Record Type: ${recordType.name}")
            info("Item: ${BuiltInRegistries.ITEM.getKey(this)}")
            info("Is Damageable: ${itemStack.isDamageableItem}")
            info("Max Damage: ${getMaxDamage(itemStack)}")
            info("Current Damage: ${itemStack.damageValue}")
            info("Remaining Uses: ${if (itemStack.isDamageableItem) getMaxDamage(itemStack) - itemStack.damageValue else "Infinite"}")
            info("Config Durability: ${Config.getRecordDurability(recordType) ?: "Unbreakable"}")
            info("================================")
        }

        // Only apply damage on server side
        if (!pLevel.isClientSide && itemStack.isDamageableItem) {
            itemStack.hurtAndBreak(1, pPlayer) { player ->
                player.broadcastBreakEvent(pUsedHand)
            }
            return InteractionResultHolder.success(itemStack)
        }

        return InteractionResultHolder.sidedSuccess(itemStack, pLevel.isClientSide)
    }

    override fun getMaxDamage(stack: ItemStack): Int {
        // Use config value if available, otherwise fall back to item properties
        val configDurability = try {
            Config.getRecordDurability(recordType)
        } catch (e: Exception) {
            null
        }
        return configDurability ?: super.getMaxDamage(stack)
    }

    override fun getDescriptionId(): String {
        return "item.${ForgeRegistries.ITEMS.getKey(this)?.namespace}.${ForgeRegistries.ITEMS.getKey(this)?.path}"
    }
}

