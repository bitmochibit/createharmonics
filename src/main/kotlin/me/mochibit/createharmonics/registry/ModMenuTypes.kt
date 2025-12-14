package me.mochibit.createharmonics.registry

import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger
import net.minecraft.core.registries.Registries
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.registries.DeferredRegister

object ModMenuTypes : AutoRegistrable {
    private val MENUS = DeferredRegister.create(Registries.MENU, CreateHarmonicsMod.MOD_ID)

    override fun register(
        eventBus: IEventBus,
        context: FMLJavaModLoadingContext,
    ) {
        Logger.info("Registering menu types for ${CreateHarmonicsMod.MOD_ID}")
        MENUS.register(eventBus)
    }
}
