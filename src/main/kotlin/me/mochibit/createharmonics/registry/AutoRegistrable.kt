package me.mochibit.createharmonics.registry

import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext

sealed interface AutoRegistrable {
    fun register(
        eventBus: IEventBus,
        context: FMLJavaModLoadingContext,
    )
}
