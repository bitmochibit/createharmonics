package me.mochibit.createharmonics.handler

import me.mochibit.createharmonics.content.records.EtherealRecordItem
import me.mochibit.createharmonics.event.crafting.RecipeAssembledEvent
import me.mochibit.createharmonics.foundation.eventbus.EventBus
import me.mochibit.createharmonics.foundation.registry.ModDataComponents
import me.mochibit.createharmonics.foundation.registry.ModItems
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData

object RecordCraftingHandler : CommonEventHandler {
    private const val CRAFTED_WITH_DISC_KEY = "crafted_with_disc"

    private fun isVanillaDisc(stack: ItemStack): Boolean = stack.item.defaultInstance.has(DataComponents.JUKEBOX_PLAYABLE)

    private fun isBaseRecord(stack: ItemStack): Boolean = stack.item == ModItems.BASE_RECORD.get()

    private fun isEtherealRecord(stack: ItemStack): Boolean = stack.item is EtherealRecordItem

    fun getCraftedWithDisc(stack: ItemStack): ItemStack {
        val resLocStr = getOrMigrateCraftedWith(stack) ?: return ItemStack.EMPTY
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
        stack.set(ModDataComponents.CRAFTED_WITH, resLoc.toString())
    }

    private fun getOrMigrateCraftedWith(stack: ItemStack): String? {
        stack.get(ModDataComponents.CRAFTED_WITH)?.let { return it }

        val legacyValue =
            stack
                .get(DataComponents.CUSTOM_DATA)
                ?.copyTag()
                ?.getString(CRAFTED_WITH_DISC_KEY)
                ?.takeIf { it.isNotEmpty() } ?: return null

        stack.set(ModDataComponents.CRAFTED_WITH, legacyValue)
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY) { data ->
            data.update { tag -> tag.remove(CRAFTED_WITH_DISC_KEY) }
        }

        return legacyValue
    }

    private fun transferCraftedWithDisc(
        etherealStack: ItemStack,
        baseStack: ItemStack,
    ) {
        val resLocStr = getOrMigrateCraftedWith(baseStack) ?: return
        etherealStack.set(ModDataComponents.CRAFTED_WITH, resLocStr)
    }

    override fun setupEvents() {
        EventBus.on<RecipeAssembledEvent> { event ->
            val (ingredients, result) = event
            result.forEach { resultStack ->
                when {
                    isBaseRecord(resultStack) -> {
                        ingredients
                            .firstOrNull { isVanillaDisc(it) }
                            ?.let { setCraftedWithDisc(resultStack, it) }
                    }

                    isEtherealRecord(resultStack) -> {
                        ingredients
                            .firstOrNull { isBaseRecord(it) || isEtherealRecord(it) }
                            ?.let { source ->
                                val originalDisc = getCraftedWithDisc(source)
                                if (!originalDisc.isEmpty) transferCraftedWithDisc(resultStack, source)
                            }
                    }
                }
            }
        }
    }
}
