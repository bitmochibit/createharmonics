package me.mochibit.createharmonics.handler

import me.mochibit.createharmonics.content.records.EtherealRecordItem
import me.mochibit.createharmonics.event.crafting.RecipeAssembledEvent
import me.mochibit.createharmonics.foundation.eventbus.EventBus
import me.mochibit.createharmonics.foundation.registry.ModItems
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.RecordItem

object RecordCraftingHandler : CommonEventHandler {
    const val CRAFTED_WITH_DISC_KEY = "crafted_with_disc"

    private fun isVanillaDisc(stack: ItemStack): Boolean {
        val item = stack.item
        return item is RecordItem
    }

    private fun isBaseRecord(stack: ItemStack): Boolean {
        val item = stack.item
        return item == ModItems.BASE_RECORD.get()
    }

    private fun isEtherealRecord(stack: ItemStack): Boolean = stack.item is EtherealRecordItem

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

    override fun setupEvents() {
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
}
