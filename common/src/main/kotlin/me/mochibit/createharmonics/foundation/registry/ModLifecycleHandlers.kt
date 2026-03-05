package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.audio.AudioPlayerManager
import me.mochibit.createharmonics.audio.process.ProcessLifecycleManager
import me.mochibit.createharmonics.event.proxy.ClientDisconnectedEventProxy
import me.mochibit.createharmonics.event.proxy.GameShuttingDownEventProxy
import me.mochibit.createharmonics.event.proxy.LevelUnloadEventProxy
import me.mochibit.createharmonics.foundation.async.ModCoroutineScope
import me.mochibit.createharmonics.foundation.eventbus.EventBus

object ModLifecycleHandlers : CommonRegistry {
    override fun register() {
        EventBus.on<ClientDisconnectedEventProxy> { event ->
            ProcessLifecycleManager.shutdownAll()
            ModCoroutineScope.cancelAll()
        }

        EventBus.on<GameShuttingDownEventProxy> { event ->
            ProcessLifecycleManager.shutdownAll()
            ModCoroutineScope.shutdown()
        }
    }
}
