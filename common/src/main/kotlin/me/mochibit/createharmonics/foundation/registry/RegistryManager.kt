package me.mochibit.createharmonics.foundation.registry

import kotlin.reflect.KClass

object RegistryManager {
    /**
     * Registers all mod registries sorted by their registration order.
     * Lower [AutoRegistrable.registrationOrder] values are registered first.
     */
    fun registerAll() {
        val autoRegistrable: List<KClass<out AutoRegistrable>> = AutoRegistrable::class.sealedSubclasses
        autoRegistrable
            .mapNotNull { it.objectInstance }
            .sortedBy { it.registrationOrder }
            .forEach { it.register() }
    }
}
