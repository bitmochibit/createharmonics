package me.mochibit.createharmonics.foundation.registry

interface CrossPlatformRegistry<RegistryObjectType, MinecraftEntry> {
    data class ConvertibleEntry<RegistryObjectType, MinecraftEntry>(
        val registryObject: RegistryObjectType,
        val get: () -> MinecraftEntry,
    )

    fun String.register(): ConvertibleEntry<RegistryObjectType, MinecraftEntry>
}
