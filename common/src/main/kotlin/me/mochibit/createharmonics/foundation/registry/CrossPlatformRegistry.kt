package me.mochibit.createharmonics.foundation.registry

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Interface for defining interchangeable registry entries between common and platform-specific objects.
 * The way it works is to simply create a map between the platform-specific registry object and minecraft's registry object.
 *
 * Beware of patched content for minecraft objects!
 */
interface CrossPlatformRegistry<RegistryObjectType, MinecraftEntry> {
    data class ConvertibleEntry<RegistryObjectType, MinecraftEntry>(
        val registryObject: RegistryObjectType,
        val mcEntrySupplier: () -> MinecraftEntry,
    ) : ReadOnlyProperty<Any?, MinecraftEntry> {
        override fun getValue(
            thisRef: Any?,
            property: KProperty<*>,
        ): MinecraftEntry = mcEntrySupplier()
    }

    fun String.register(): ConvertibleEntry<RegistryObjectType, MinecraftEntry>
}
