package me.mochibit.createharmonics.registry

import me.mochibit.createharmonics.CreateHarmonics
import me.mochibit.createharmonics.CreateHarmonicsMod
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.RegistryObject

object ModCreativeTabs : AutoRegistrable {
    private val CREATIVE_MODE_TABS: DeferredRegister<CreativeModeTab> =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateHarmonicsMod.MOD_ID)

    val MAIN_TAB: RegistryObject<CreativeModeTab> =
        CREATIVE_MODE_TABS.register("main") {
            CreativeModeTab
                .builder()
                .title(Component.translatable("itemGroup.${CreateHarmonicsMod.MOD_ID}"))
                .icon { ItemStack(Items.MUSIC_DISC_13) }
                .displayItems { _, output ->
                    output.acceptAll(CreateHarmonics.getRegistrate().getAll(Registries.ITEM).map { ItemStack(it.get()) })
                }.build()
        }

    override fun register(
        eventBus: IEventBus,
        context: FMLJavaModLoadingContext,
    ) {
        CREATIVE_MODE_TABS.register(eventBus)
    }
}
