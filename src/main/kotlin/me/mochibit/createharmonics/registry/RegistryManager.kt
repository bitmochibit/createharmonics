package me.mochibit.createharmonics.registry

import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import kotlin.reflect.KClass

object RegistryManager {
    /**
     * Registers all mod registries
     * @param eventBus The Forge mod event bus to register to
     */
    fun registerAll(
        eventBus: IEventBus,
        context: FMLJavaModLoadingContext,
    ) {
        val autoRegistrable: List<KClass<out AutoRegistrable>> = AutoRegistrable::class.sealedSubclasses
        for (registry in autoRegistrable) {
            val registry = registry.objectInstance ?: continue
            registry.register(eventBus, context)
        }
    }
}
