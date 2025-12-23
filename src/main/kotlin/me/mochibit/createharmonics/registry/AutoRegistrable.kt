package me.mochibit.createharmonics.registry

import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext

sealed interface AutoRegistrable {
    /**
     * The registration order priority. Lower values are registered first.
     * Override this property to control registration order relative to other AutoRegistrable implementations.
     * Default is 0, increase the value for registrations that depend on others (e.g., Ponders = 5).
     */
    val registrationOrder: Int
        get() = 0

    fun register(
        eventBus: IEventBus,
        context: FMLJavaModLoadingContext,
    )
}
