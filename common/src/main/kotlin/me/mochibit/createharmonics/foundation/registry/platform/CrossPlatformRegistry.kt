package me.mochibit.createharmonics.foundation.registry.platform

import org.valkyrienskies.core.impl.shadow.it
import java.util.EnumMap
import java.util.function.Supplier
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Interface for defining interchangeable registry entries between common and platform-specific objects.
 * The way it works is to simply create a map between the platform-specific registry object and minecraft's registry object.
 *
 * Beware of patched content for minecraft objects!
 */
abstract class AbstractCrossPlatformRegistry<RegistryObjectType, MinecraftEntry> {
    val referenceMap: MutableMap<MinecraftEntry, RegistryObjectType> = mutableMapOf()

    data class ConvertibleEntry<RegistryObjectType, MinecraftEntry>(
        val registry: AbstractCrossPlatformRegistry<RegistryObjectType, MinecraftEntry>,
        val registryObject: RegistryObjectType,
        val mcEntrySupplier: () -> MinecraftEntry,
    ) : ReadOnlyProperty<Any?, MinecraftEntry> {
        override fun getValue(
            thisRef: Any?,
            property: KProperty<*>,
        ): MinecraftEntry {
            val mcEntry = mcEntrySupplier()
            registry.referenceMap.putIfAbsent(mcEntry, registryObject)
            return mcEntry
        }
    }

    fun MinecraftEntry.registryObject(): RegistryObjectType =
        this@AbstractCrossPlatformRegistry.referenceMap[this]
            ?: throw IllegalStateException("A weird issue occurred: $this was not registered")

    abstract fun registerEntry(name: String): ConvertibleEntry<RegistryObjectType, MinecraftEntry>
}

fun <T> Supplier<T>.asDelegate(): ReadOnlyProperty<Any?, T> = ReadOnlyProperty { _, _ -> this.get() }

fun <T> asDelegate(supplier: () -> T): ReadOnlyProperty<Any?, T> = ReadOnlyProperty { _, _ -> supplier() }
