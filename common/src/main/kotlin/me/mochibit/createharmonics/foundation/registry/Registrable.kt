package me.mochibit.createharmonics.foundation.registry

interface Registrable {
    /**
     * The registration order priority. Lower values are registered first.
     * Override this property to control registration order relative to other AutoRegistrable implementations.
     * Default is 0, increase the value for registrations that depend on others (e.g., Ponders = 5).
     */
    val registrationOrder: Int
        get() = 0

    fun register()
}

sealed interface CommonRegistry : Registrable

/**
 * Generic auto-registration function utility that uses sealed classes to automatically register, and discover, Registrable implementations.
 *
 * Lower [Registrable.registrationOrder] values are registered first.
 *
 * Pass a sealed interface that extends [Registrable] as the type parameter to automatically register all of its object implementations.
 *
 * Place the marker in the same package as the implementations!!
 *
 * It is platform-agnostic
 */
inline fun <reified AutoRegistrableMarker : Registrable> autoRegister() {
    if (!AutoRegistrableMarker::class.isSealed) {
        throw IllegalArgumentException("The passed auto registrable marker must be a sealed interface to enable automatic registration")
    }

    AutoRegistrableMarker::class
        .sealedSubclasses
        .filter { AutoRegistrableMarker::class.java.isAssignableFrom(it.java) }
        .map { it.objectInstance as Registrable }
        .sortedBy { it.registrationOrder }
        .forEach { it.register() }
}
