package me.mochibit.createharmonics.foundation.eventbus

import kotlin.reflect.KClass

abstract class PlatformEventBridge {
    protected val registeredProxyTypes = mutableSetOf<KClass<out ProxyEvent>>()

    protected abstract fun setupProxyEvents()

    fun setup() {
        setupProxyEvents()
        val missing = ProxyEvent::class.sealedSubclasses.toSet() - registeredProxyTypes
        check(missing.isEmpty()) {
            "Missing proxy registrations: ${missing.map { it.simpleName }}"
        }
    }
}
