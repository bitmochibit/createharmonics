package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.audio.process.ProcessLifecycleManager
import me.mochibit.createharmonics.event.proxy.ProxyEvent
import me.mochibit.createharmonics.foundation.async.ModCoroutineScope
import me.mochibit.createharmonics.foundation.eventbus.EventBus

object ModLifecycleHandlers : CommonRegistry {
    override fun register() {
        EventBus.on<ProxyEvent.ClientDisconnectedEventProxy> { event ->
            ProcessLifecycleManager.shutdownAll()
            ModCoroutineScope.cancelAll()
        }

        EventBus.on<ProxyEvent.GameShuttingDownEventProxy> { event ->
            ProcessLifecycleManager.shutdownAll()
            ModCoroutineScope.shutdown()
        }
    }
}
