package me.mochibit.createharmonics.foundation.registry

object RegistryManager {
    /**
     * Registers all mod registries sorted by their registration order.
     * Lower [Registrable.registrationOrder] values are registered first.
     * It is platform-agnostic
     */
    fun registerAll(vararg registries: Registrable) {
        registries
            .sortedBy { it.registrationOrder }
            .forEach { it.register() }
    }
}
