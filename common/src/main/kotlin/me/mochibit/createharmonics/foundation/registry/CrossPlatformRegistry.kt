package me.mochibit.createharmonics.foundation.registry

/**
 * Interface for defining interchangeable registry entries between common and platform-specific objects.
 * The way it works is to simply create a map between the platform-specific registry object and minecraft's registry object.
 *
 * Beware of patched content for minecraft objects!
 */
interface CrossPlatformRegistry<RegistryObjectType, MinecraftEntry> {
    data class ConvertibleEntry<RegistryObjectType, MinecraftEntry>(
        val registryObject: RegistryObjectType,
        val get: () -> MinecraftEntry,
    )

    fun String.register(): ConvertibleEntry<RegistryObjectType, MinecraftEntry>
}
