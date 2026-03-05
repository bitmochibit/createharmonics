package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.foundation.eventbus.ProxyEvent
import me.mochibit.createharmonics.foundation.info

object ModEventProxy : CommonRegistry {
    override fun register() {
        "Registering mod event proxies...".info()
        ProxyEvent::class
            .sealedSubclasses
            .filter { ProxyEvent::class.java.isAssignableFrom(it.java) }
            .map { it.objectInstance as ProxyEvent }
            .forEach { it.proxyRegistrar() }
    }
}
