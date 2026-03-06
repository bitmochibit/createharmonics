package me.mochibit.createharmonics.foundation.eventbus

import me.mochibit.createharmonics.event.proxy.ProxyEvent
import kotlin.reflect.KClass

abstract class PlatformEventBridge {
    protected val registeredProxyTypes = mutableSetOf<KClass<out ProxyEvent>>()

    protected abstract fun setupProxyEvents()

    fun setup() {
        setupProxyEvents()
        // Derived automatically from the sealed hierarchy — no list to maintain.
        val missing = ProxyEvent::class.sealedSubclasses.toSet() - registeredProxyTypes
        check(missing.isEmpty()) {
            "Missing proxy registrations: ${missing.map { it.simpleName }}"
        }
    }
}
