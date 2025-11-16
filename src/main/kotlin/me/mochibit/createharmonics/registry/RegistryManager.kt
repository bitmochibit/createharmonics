package me.mochibit.createharmonics.registry

import net.minecraftforge.eventbus.api.IEventBus

/**
 * Manages automatic discovery and registration of mod registries.
 * This class scans for all classes implementing AbstractModRegistry that are
 * annotated with @AutoRegister and registers them in priority order.
 */
object RegistryManager {

    val registries = listOf<AbstractModRegistry>(
        ModBlocksRegistry,
        ModBlockEntitiesRegistry,
        ModItemsRegistry,
        ModCreativeTabs,
        ModMenuTypesRegistry,
        ModArmInteractionPointRegistry,
    )


    /**
     * Registers all mod registries
     * @param eventBus The Forge mod event bus to register to
     */
    fun registerAll(eventBus: IEventBus) {
        for (registry in registries) {
            registry.register(eventBus)
        }

    }
}
