package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.audio.AudioPlayerManager
import me.mochibit.createharmonics.audio.process.ProcessLifecycleManager
import me.mochibit.createharmonics.foundation.async.ClientCoroutineScope
import me.mochibit.createharmonics.foundation.async.ModCoroutineScope
import me.mochibit.createharmonics.foundation.async.ServerCoroutineScope
import me.mochibit.createharmonics.foundation.eventbus.ClientEvents
import me.mochibit.createharmonics.foundation.eventbus.CommonEvents
import me.mochibit.createharmonics.foundation.eventbus.EventBus
import me.mochibit.createharmonics.foundation.eventbus.ProxyEvent
import me.mochibit.createharmonics.foundation.eventbus.ServerEvents

object ModLifecycleHandlers : CommonRegistry {
    override fun register() {
        // Client disconnects from a server (including leaving singleplayer/LAN)
        EventBus.on<ClientEvents.ClientDisconnectedEvent> { _ ->
            // Close all audio players first (while MC thread is still alive) so they
            // stop cleanly, then cancel the scope so no orphan coroutines remain.
            AudioPlayerManager.closeAll()
            ProcessLifecycleManager.shutdownAll()
            ClientCoroutineScope.cancelAll()
        }

        // Dedicated/integrated server stops
        EventBus.on<ServerEvents.ServerStoppedEvent> { _ ->
            ServerCoroutineScope.cancelAll()
        }

        // Game is fully closing
        EventBus.on<CommonEvents.GameShuttingDownEvent> { _ ->
            AudioPlayerManager.closeAll()
            ProcessLifecycleManager.shutdownAll()
            ClientCoroutineScope.shutdown()
            ServerCoroutineScope.shutdown()
            ModCoroutineScope.shutdown()
        }
    }
}
