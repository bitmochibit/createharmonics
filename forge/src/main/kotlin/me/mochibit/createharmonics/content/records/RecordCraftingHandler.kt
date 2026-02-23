package me.mochibit.createharmonics.content.records

import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.event.crafting.RecipeAssembledEvent
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.RecordItem
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.registries.ForgeRegistries

object RecordCraftingHandler {
    const val CRAFTED_WITH_DISC_KEY = "crafted_with_disc"

    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = CreateHarmonicsMod.MOD_ID)
    object Handler {
        @JvmStatic
        @SubscribeEvent
        fun onRecordCrafted(event: RecipeAssembledEvent) {
            event.result.forEach { resultStack ->
                when (resultStack.item) {
                    is BaseRecordItem -> {
                        val discUsed = event.ingredients.firstOrNull { it.item is RecordItem }
                        discUsed?.let { setCraftedWithDisc(resultStack, it) }
                    }

                    is EtherealRecordItem -> {
                        val baseRecordUsed =
                            event.ingredients.firstOrNull { it.item is BaseRecordItem || it.item is EtherealRecordItem }
                        baseRecordUsed?.let {
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
        val recordItem = ForgeRegistries.ITEMS.getValue(resLoc) as? RecordItem ?: return ItemStack.EMPTY

        return ItemStack(recordItem)
    }

    fun setCraftedWithDisc(
        stack: ItemStack,
        withDisc: ItemStack,
    ) {
        if (stack.item !is BaseRecordItem && stack.item !is EtherealRecordItem) return

        if (stack.tag == null) {
            stack.tag = CompoundTag()
        }

        val resLoc = ForgeRegistries.ITEMS.getKey(withDisc.item) ?: return
        stack.tag?.putString(CRAFTED_WITH_DISC_KEY, resLoc.toString())
    }

    private fun transferCraftedWithDisc(
        etherealStack: ItemStack,
        baseStack: ItemStack,
    ) {
        val baseTag = baseStack.tag ?: return
        val resLocStr = baseTag.getString(CRAFTED_WITH_DISC_KEY)
        if (resLocStr.isEmpty()) return

        if (etherealStack.tag == null) {
            etherealStack.tag = CompoundTag()
        }

        etherealStack.tag?.putString(CRAFTED_WITH_DISC_KEY, resLocStr)
    }
}
