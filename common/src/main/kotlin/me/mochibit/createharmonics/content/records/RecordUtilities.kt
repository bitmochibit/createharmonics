package me.mochibit.createharmonics.content.records

import me.mochibit.createharmonics.foundation.registry.ModDataComponents
import me.mochibit.createharmonics.foundation.registry.ModItems
import me.mochibit.createharmonics.handler.RecordCraftingHandler
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

/**
 * Record utilities
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new RecordUtilities
 */
object RecordUtilities {
    private fun legacyUrl(stack: ItemStack): String? =
        stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getString("audio_url")?.takeIf { it.isNotEmpty() }

    /**
     * Returns the audio url
     *
     * @param stack
     * @return the string
     */
    fun getAudioUrl(stack: ItemStack): String? {
        if (stack.item !is EtherealRecordItem) return null
        return stack.get(ModDataComponents.RECORD_URL) ?: legacyUrl(stack)
    }

    /**
     * Migrate legacy url
     *
     * @param stack
     */
    fun migrateLegacyUrl(stack: ItemStack) {
        if (stack.item !is EtherealRecordItem) return
        val legacy = legacyUrl(stack) ?: return
        if (stack.get(ModDataComponents.RECORD_URL) == null) stack.set(ModDataComponents.RECORD_URL, legacy)
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY) { d -> d.update { it.remove("audio_url") } }
    }

    /**
     * Sets the audio url
     *
     * @param stack
     * @param url
     */
    fun setAudioUrl(
        stack: ItemStack,
        url: String,
    ) {
        if (stack.item !is EtherealRecordItem) return

        stack.set(ModDataComponents.RECORD_URL, url)
    }

    /**
     * Handles record usage by applying durability damage.
     *
     * @param stack The ethereal record item stack
     * @param random Random source for durability calculations
     * @return RecordUseResult containing the result of the usage
     * @param level
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

    /**
     * Converts the broken record stack
     *
     * @param stack
     * @return the item stack
     */
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

    /**
     * Returns the broken record stack
     *
     * @param stack
     * @return the item stack
     */
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
     *
     * @constructor Creates a new RecordUseResult
     */
    sealed class RecordUseResult {
        /**
         * The record is not damageable (infinite uses)
         *
         * @constructor Creates a new NotDamageable
         * @property stack
         */
        data class NotDamageable(
            val stack: ItemStack,
        ) : RecordUseResult()

        /**
         * The record was damaged but not broken
         *
         * @constructor Creates a new Damaged
         * @property stack
         */
        data class Damaged(
            val stack: ItemStack,
        ) : RecordUseResult()

        /**
         * The record broke and should drop the base record
         *
         * @constructor Creates a new Broken
         * @property dropStack the drop stack
         */
        data class Broken(
            val dropStack: ItemStack,
        ) : RecordUseResult()

        /**
         * Invalid input (not an ethereal record)
         *
         * @constructor Creates a new Invalid
         */
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

/**
 * Returns `true` if ethereal record on the receiver [ItemStack]
 *
 * @return `true` if the condition holds, `false` otherwise
 */
fun ItemStack.isEtherealRecord(): Boolean = item is EtherealRecordItem

/**
 * Returns `true` if broken ethereal record on the receiver [ItemStack]
 *
 * @return `true` if the condition holds, `false` otherwise
 */
fun ItemStack.isBrokenEtherealRecord(): Boolean = (item as? EtherealRecordItem)?.isRecordBroken() == true

/**
 * Returns `true` if assigned url on the receiver [ItemStack]
 *
 * @return `true` if the condition holds, `false` otherwise
 */
fun ItemStack.hasAssignedUrl(): Boolean = !RecordUtilities.getAudioUrl(this).isNullOrEmpty()
