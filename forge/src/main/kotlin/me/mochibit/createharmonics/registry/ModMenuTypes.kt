package me.mochibit.createharmonics.registry

import me.mochibit.createharmonics.ForgeCreateHarmonicsMod
import net.minecraft.core.registries.Registries
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.registries.DeferredRegister

object ModMenuTypes : AutoRegistrable {
    private val MENUS = DeferredRegister.create(Registries.MENU, ForgeCreateHarmonicsMod.MOD_ID)

    override fun register(
        eventBus: IEventBus,
        context: FMLJavaModLoadingContext,
    ) {
        MENUS.register(eventBus)
    }
}
