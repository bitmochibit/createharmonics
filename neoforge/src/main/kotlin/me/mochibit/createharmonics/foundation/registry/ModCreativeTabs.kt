package me.mochibit.createharmonics.foundation.registry

import com.simibubi.create.foundation.data.CreateRegistrate
import com.tterrag.registrate.util.entry.RegistryEntry
import me.mochibit.createharmonics.CreateHarmonicsMod.MOD_ID
import me.mochibit.createharmonics.ModEventBus
import me.mochibit.createharmonics.ModRegistrate
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.foundation.locale.ModLang
import me.mochibit.createharmonics.foundation.registry.ModItems.etherealRecord
import net.createmod.catnip.platform.CatnipServices
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Predicate
import java.util.function.Supplier

object ModCreativeTabs : NeoforgeRegistry {
    override val registrationOrder = 4

    private val CREATIVE_MODE_TABS: DeferredRegister<CreativeModeTab> =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID)

    val MAIN_TAB: DeferredHolder<CreativeModeTab, CreativeModeTab> =
        CREATIVE_MODE_TABS.register(
            "main",
            Supplier {
                CreativeModeTab
                    .builder()
                    .title(ModLang.translate("item_group").component())
                    .icon { ItemStack(ModItems.etherealRecord(RecordType.BRASS)) }
                    .displayItems(DisplayItemsGenerator(MAIN_TAB))
                    .build()
            },
        )

    override fun register() {
        CREATIVE_MODE_TABS.register(ModEventBus)
    }

    private class DisplayItemsGenerator(
        private val tab: DeferredHolder<CreativeModeTab, CreativeModeTab>,
    ) : CreativeModeTab.DisplayItemsGenerator {
        override fun accept(
            params: CreativeModeTab.ItemDisplayParameters,
            output: CreativeModeTab.Output,
        ) {
            val exclusionPredicate = makeExclusionPredicate()
            val orderings = makeOrderings()
            val stackFunc = makeStackFunc()
            val visibilityFunc = makeVisibilityFunc()

            val items = mutableListOf<Item>()

            items += collectItems(exclusionPredicate.or(IS_ITEM_3D.negate()))
            items += collectBlocks(exclusionPredicate)
            items += collectItems(exclusionPredicate.or(IS_ITEM_3D))

            applyOrderings(items, orderings)
            outputAll(output, items, stackFunc, visibilityFunc)
        }

        private fun collectBlocks(exclusionPredicate: Predicate<Item>): List<Item> =
            ModRegistrate
                .getAll(Registries.BLOCK)
                .mapNotNull { entry -> entry.get().asItem().takeIf { it != Items.AIR } }
                .filter { !exclusionPredicate.test(it) }
                .distinct()

        private fun collectItems(exclusionPredicate: Predicate<Item>): List<Item> =
            ModRegistrate
                .getAll(Registries.ITEM)
                .map { entry -> entry.get() }
                .filter { it !is BlockItem && !exclusionPredicate.test(it) }

        private fun applyOrderings(
            items: MutableList<Item>,
            orderings: List<ItemOrdering>,
        ) {
            for (ordering in orderings) {
                val anchorIndex = items.indexOf(ordering.anchor).takeIf { it != -1 } ?: continue
                val itemIndex = items.indexOf(ordering.item).takeIf { it != -1 } ?: continue

                items.removeAt(itemIndex)
                val adjustedAnchor = if (itemIndex < anchorIndex) anchorIndex - 1 else anchorIndex

                when (ordering.type) {
                    ItemOrdering.Type.BEFORE -> items.add(adjustedAnchor, ordering.item)
                    ItemOrdering.Type.AFTER -> items.add(adjustedAnchor + 1, ordering.item)
                }
            }
        }

        private fun outputAll(
            output: CreativeModeTab.Output,
            items: List<Item>,
            stackFunc: (Item) -> ItemStack,
            visibilityFunc: (Item) -> CreativeModeTab.TabVisibility,
        ) {
            for (item in items) {
                output.accept(stackFunc(item), visibilityFunc(item))
            }
        }
    }

    private fun makeExclusionPredicate(): Predicate<Item> {
        val exclusions = mutableSetOf<Item>()

        // exclusions += ModItems.SOME_INTERNAL_ITEM.asItem()

        return Predicate { it in exclusions }
    }

    private fun makeOrderings(): List<ItemOrdering> {
        val orderings = mutableListOf<ItemOrdering>()

        // orderings += ItemOrdering.before(ModItems.NEEDLE.asItem(), ModBlocks.RECORD_PLAYER.asItem())

        return orderings
    }

    private fun makeStackFunc(): (Item) -> ItemStack {
        val factories = mutableMapOf<Item, (Item) -> ItemStack>()

        // Example — pre-set a record property:
        // factories[ModItems.EtherealRecord ... ] = { item ->
        //     ItemStack(item).also { it.set(...) }
        // }

        return { item -> factories[item]?.invoke(item) ?: ItemStack(item) }
    }

    private fun makeVisibilityFunc(): (Item) -> CreativeModeTab.TabVisibility {
        val visibilities = mutableMapOf<Item, CreativeModeTab.TabVisibility>()

        // Example — hide colour variants beyond the default from the main tab:
        // ModBlocks.DYED_SPEAKERS.forEach { entry ->
        //     if (entry.get().color != DyeColor.WHITE)
        //         visibilities[entry.asItem()] = CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY
        // }

        return { item ->
            visibilities[item] ?: CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
        }
    }

    private val IS_ITEM_3D: Predicate<Item> by lazy {
        if (CatnipServices.PLATFORM.env.isClient) {
            Predicate { item ->
                val renderer = Minecraft.getInstance().itemRenderer
                val model = renderer.getModel(ItemStack(item), null, null, 0)
                model.isGui3d
            }
        } else {
            Predicate { false }
        }
    }

    private data class ItemOrdering(
        val item: Item,
        val anchor: Item,
        val type: Type,
    ) {
        enum class Type { BEFORE, AFTER }

        companion object {
            fun before(
                item: Item,
                anchor: Item,
            ) = ItemOrdering(item, anchor, Type.BEFORE)

            fun after(
                item: Item,
                anchor: Item,
            ) = ItemOrdering(item, anchor, Type.AFTER)
        }
    }
}
