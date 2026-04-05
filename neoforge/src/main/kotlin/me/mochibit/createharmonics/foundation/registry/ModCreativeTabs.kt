package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.CreateHarmonicsMod.MOD_ID
import me.mochibit.createharmonics.ModEventBus
import me.mochibit.createharmonics.ModRegistrate
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.foundation.locale.ModLang
import me.mochibit.createharmonics.foundation.registry.ModItems.etherealRecord
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
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
                    .icon { ItemStack(ModItems etherealRecord RecordType.BRASS) }
                    .displayItems { _, output ->
                        output.acceptAll(ModRegistrate.getAll(Registries.ITEM).map { it.get().defaultInstance })
                    }.build()
            },
        )

    override fun register() {
        CREATIVE_MODE_TABS.register(ModEventBus)
    }
}
