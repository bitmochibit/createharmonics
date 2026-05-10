package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.foundation.registry.platform.bridge.CommonAbstractRegistry
import net.minecraftforge.registries.DeferredRegister
import java.util.function.Supplier

interface ForgeRegistryBinder<T> {
    val deferredRegister: DeferredRegister<T>

    fun bindAll(registry: CommonAbstractRegistry<T>) {
        registry.pending.forEach { entry ->
            @Suppress("UNCHECKED_CAST")
            val typedEntry = entry as CommonAbstractRegistry.PendingEntry<T>
            val holder = deferredRegister.register(typedEntry.name, Supplier { typedEntry.factory() })
            typedEntry.ref.bind { holder.get() }
        }
    }
}
