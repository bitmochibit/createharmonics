package me.mochibit.createharmonics.foundation.registry

import kotlin.reflect.KClass

object RegistryManager {
    /**
     * Registers all mod registries sorted by their registration order.
     * Lower [AutoRegistrable.registrationOrder] values are registered first.
     * It is platform-agnostic
     */
    fun registerAll(vararg registries: AutoRegistrable) {
        registries
            .sortedBy { it.registrationOrder }
            .forEach { it.register() }
    }
}
