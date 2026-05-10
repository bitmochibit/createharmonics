package me.mochibit.createharmonics.foundation.registry.platform.bridge

import kotlin.reflect.KProperty

class RegistryRef<T>(
    val name: String,
) {
    private var supplier: (() -> T)? = null

    fun bind(supplier: () -> T) {
        check(this.supplier == null) { "RegistryRef '$name' already bound" }
        this.supplier = supplier
    }

    val value: T
        get() = supplier?.invoke() ?: error("RegistryRef '$name' not bound, registration not completed")

    operator fun getValue(
        thisRef: Any?,
        prop: KProperty<*>,
    ): T = value
}

abstract class CommonAbstractRegistry<T> {
    class PendingEntry<R>(
        val name: String,
        val ref: RegistryRef<R>,
        val factory: () -> R,
    )

    private val _pending = mutableListOf<PendingEntry<out T>>()
    val pending: List<PendingEntry<out T>> get() = _pending

    protected fun <R : T> entry(
        name: String,
        factory: () -> R,
    ): RegistryRef<R> {
        val ref = RegistryRef<R>(name)
        _pending += PendingEntry(name, ref, factory)
        return ref
    }
}
