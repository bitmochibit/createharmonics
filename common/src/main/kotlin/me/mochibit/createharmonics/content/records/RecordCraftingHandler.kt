package me.mochibit.createharmonics.content.records

import me.mochibit.createharmonics.event.crafting.RecipeAssembledEvent
import me.mochibit.createharmonics.foundation.eventbus.EventBus
import me.mochibit.createharmonics.foundation.eventbus.ModEvent
import me.mochibit.createharmonics.foundation.registry.ModItems
import me.mochibit.createharmonics.foundation.services.contentService
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.RecordItem
import org.valkyrienskies.core.impl.shadow.it

object RecordCraftingHandler {
    const val CRAFTED_WITH_DISC_KEY = "crafted_with_disc"

    private fun isVanillaDisc(stack: ItemStack): Boolean {
        val item = stack.item
        return item is RecordItem
    }

    private fun isBaseRecord(stack: ItemStack): Boolean {
        val item = stack.item
        return item == ModItems.BASE_RECORD.get()
    }

    private fun isEtherealRecord(stack: ItemStack): Boolean {
        val item = stack.item
        return ModItems.ETHEREAL_RECORDS.any { it.value.get() == item }
    }

    init {
        EventBus.on<RecipeAssembledEvent> { event ->
            val (ingredients, result) = event
            result.forEach { resultStack ->
                when {
                    isBaseRecord(resultStack) -> {
                        val discUsed = ingredients.firstOrNull { isVanillaDisc(it) }
                        discUsed?.let { setCraftedWithDisc(resultStack, it) }
                    }

                    isEtherealRecord(resultStack) -> {
                        val sourceRecord = ingredients.firstOrNull { isBaseRecord(it) || isEtherealRecord(it) }
                        sourceRecord?.let {
                            val originalDisc = getCraftedWithDisc(it)
                            if (!originalDisc.isEmpty) {
                                transferCraftedWithDisc(resultStack, it)
                            }
                        }
                    }
                }
            }
        }
    }

    fun getCraftedWithDisc(stack: ItemStack): ItemStack {
        val tag = stack.tag ?: return ItemStack.EMPTY
        val resLocStr = tag.getString(CRAFTED_WITH_DISC_KEY)
        if (resLocStr.isEmpty()) return ItemStack.EMPTY

        val resLoc = ResourceLocation.tryParse(resLocStr) ?: return ItemStack.EMPTY
        val item = BuiltInRegistries.ITEM.get(resLoc)

        return if (item == Items.AIR) ItemStack.EMPTY else ItemStack(item)
    }

    fun setCraftedWithDisc(
        stack: ItemStack,
        withDisc: ItemStack,
    ) {
        if (!isBaseRecord(stack) && !isEtherealRecord(stack)) return

        val resLoc = BuiltInRegistries.ITEM.getKey(withDisc.item)

        if (stack.tag == null) stack.tag = CompoundTag()
        stack.tag?.putString(CRAFTED_WITH_DISC_KEY, resLoc.toString())
    }

    private fun transferCraftedWithDisc(
        etherealStack: ItemStack,
        baseStack: ItemStack,
    ) {
        val resLocStr =
            baseStack.tag
                ?.getString(CRAFTED_WITH_DISC_KEY)
                ?.takeIf { it.isNotEmpty() } ?: return

        if (etherealStack.tag == null) etherealStack.tag = CompoundTag()
        etherealStack.tag?.putString(CRAFTED_WITH_DISC_KEY, resLocStr)
    }
}
