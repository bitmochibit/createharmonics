package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.CreateHarmonicsMod.MOD_ID
import me.mochibit.createharmonics.ForgeModEntryPoint
import me.mochibit.createharmonics.ModEventBus
import me.mochibit.createharmonics.cRegistrate
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.foundation.locale.ModLang
import me.mochibit.createharmonics.foundation.registry.ModItems.etherealRecord
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.RegistryObject

object ModCreativeTabs : Registrable, ForgeRegistry {
    override val registrationOrder = 4

    private val CREATIVE_MODE_TABS: DeferredRegister<CreativeModeTab> =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID)

    val MAIN_TAB: RegistryObject<CreativeModeTab> =
        CREATIVE_MODE_TABS.register("main") {
            CreativeModeTab
                .builder()
                .title(ModLang.translate("item_group").component())
                .icon { ItemStack(ModItems etherealRecord RecordType.BRASS) }
                .displayItems { _, output ->
                    output.acceptAll(cRegistrate().getAll(Registries.ITEM).map { it.get().defaultInstance })
                }.build()
        }

    override fun register() {
        CREATIVE_MODE_TABS.register(ModEventBus)
    }
}
