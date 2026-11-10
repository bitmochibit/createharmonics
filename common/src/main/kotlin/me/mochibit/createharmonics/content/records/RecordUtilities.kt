package me.mochibit.createharmonics.content.records

import me.mochibit.createharmonics.foundation.registry.ModDataComponents
import me.mochibit.createharmonics.foundation.registry.ModItems
import me.mochibit.createharmonics.handler.RecordCraftingHandler
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

object RecordUtilities {
    private fun legacyUrl(stack: ItemStack): String? =
        stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getString("audio_url")?.takeIf { it.isNotEmpty() }
    fun getAudioUrl(stack: ItemStack): String? {
        if (stack.item !is EtherealRecordItem) return null
        return stack.get(ModDataComponents.RECORD_URL) ?: legacyUrl(stack)
    }

    fun migrateLegacyUrl(stack: ItemStack) {
        if (stack.item !is EtherealRecordItem) return
        val legacy = legacyUrl(stack) ?: return
        if (stack.get(ModDataComponents.RECORD_URL) == null) stack.set(ModDataComponents.RECORD_URL, legacy)
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY) { d -> d.update { it.remove("audio_url") } }
    }

    fun setAudioUrl(
        stack: ItemStack,
        url: String,
    ) {
        if (stack.item !is EtherealRecordItem) return

        stack.set(ModDataComponents.RECORD_URL, url)
    }

    /**
     * Handles record usage by applying durability damage.
     * @param stack The ethereal record item stack
     * @param random Random source for durability calculations
     * @return RecordUseResult containing the result of the usage
     */
    fun handleRecordUse(
        stack: ItemStack,
        level: ServerLevel,
    ): RecordUseResult {
        val itemType = stack.item
        if (itemType !is EtherealRecordItem) return RecordUseResult.Invalid

        if (!itemType.isDamageable(stack)) {
            return RecordUseResult.NotDamageable(stack)
        }

        // Apply damage
        val damaged = stack.copy()
        var broken = false
        damaged.hurtAndBreak(1, level, null) {
            broken = true
        }

        return if (broken) {
            val brokenItemStack = toBrokenRecordStack(stack)
            if (brokenItemStack === stack) return RecordUseResult.Invalid
            RecordUseResult.Broken(brokenItemStack)
        } else {
            RecordUseResult.Damaged(damaged)
        }
    }

    fun toBrokenRecordStack(stack: ItemStack): ItemStack {
        val recordItem = stack.item as? EtherealRecordItem ?: return stack
        val brokenItemType = ModItems.getBrokenEtherealRecordItem(recordItem.recordType)?.get() ?: return stack

        val craftedWithDisc = RecordCraftingHandler.getCraftedWithDisc(stack)
        val brokenItemStack = ItemStack(brokenItemType)
        if (!craftedWithDisc.isEmpty) {
            RecordCraftingHandler.setCraftedWithDisc(brokenItemStack, craftedWithDisc)
        }
        return brokenItemStack
    }

    fun fromBrokenRecordStack(stack: ItemStack): ItemStack {
        val brokenItemType = stack.item as? EtherealRecordItem ?: return stack
        val recordItem = ModItems.getEtherealRecordItem(brokenItemType.recordType).get() ?: return stack

        val craftedWithDisc = RecordCraftingHandler.getCraftedWithDisc(stack)
        val recordItemStack = ItemStack(recordItem)
        if (!craftedWithDisc.isEmpty) {
            RecordCraftingHandler.setCraftedWithDisc(recordItemStack, craftedWithDisc)
        }

        return recordItemStack
    }

    /**
     * Result of handling record use
     */
    sealed class RecordUseResult {
        /** The record is not damageable (infinite uses) */
        data class NotDamageable(
            val stack: ItemStack,
        ) : RecordUseResult()

        /** The record was damaged but not broken */
        data class Damaged(
            val stack: ItemStack,
        ) : RecordUseResult()

        /** The record broke and should drop the base record */
        data class Broken(
            val dropStack: ItemStack,
        ) : RecordUseResult()

        /** Invalid input (not an ethereal record) */
        data object Invalid : RecordUseResult()

        val isBroken: Boolean get() = this is Broken
        val shouldReplace: Boolean get() = this is Damaged || this is NotDamageable
        val replacementStack: ItemStack?
            get() =
                when (this) {
                    is Damaged -> stack
                    is NotDamageable -> stack
                    else -> null
                }
    }
}

fun ItemStack.isEtherealRecord(): Boolean = item is EtherealRecordItem

fun ItemStack.isBrokenEtherealRecord(): Boolean = (item as? EtherealRecordItem)?.isRecordBroken() == true

fun ItemStack.hasAssignedUrl(): Boolean = !RecordUtilities.getAudioUrl(this).isNullOrEmpty()
