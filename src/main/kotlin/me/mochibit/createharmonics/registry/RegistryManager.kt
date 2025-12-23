package me.mochibit.createharmonics.registry

import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import kotlin.reflect.KClass

object RegistryManager {
    /**
     * Registers all mod registries sorted by their registration order.
     * Lower [AutoRegistrable.registrationOrder] values are registered first.
     * @param eventBus The Forge mod event bus to register to
     */
    fun registerAll(
        eventBus: IEventBus,
        context: FMLJavaModLoadingContext,
    ) {
        val autoRegistrable: List<KClass<out AutoRegistrable>> = AutoRegistrable::class.sealedSubclasses
        autoRegistrable
            .mapNotNull { it.objectInstance }
            .sortedBy { it.registrationOrder }
            .forEach { it.register(eventBus, context) }
    }
}
