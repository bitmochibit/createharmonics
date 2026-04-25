package me.mochibit.createharmonics.foundation.registry

import kotlinx.coroutines.runBlocking
import me.mochibit.createharmonics.audio.AudioPlayerManager
import me.mochibit.createharmonics.audio.process.ProcessLifecycleManager
import me.mochibit.createharmonics.foundation.async.ClientCoroutineScope
import me.mochibit.createharmonics.foundation.async.ModCoroutineScope
import me.mochibit.createharmonics.foundation.async.ServerCoroutineScope
import me.mochibit.createharmonics.foundation.eventbus.ClientEvents
import me.mochibit.createharmonics.foundation.eventbus.CommonEvents
import me.mochibit.createharmonics.foundation.eventbus.EventBus
import me.mochibit.createharmonics.foundation.eventbus.LogicalSide
import me.mochibit.createharmonics.foundation.eventbus.ServerEvents
import me.mochibit.createharmonics.foundation.info

object ModLifecycleHandlers : CommonRegistry {
    override fun register() {
        // Client disconnects from a server (including leaving singleplayer/LAN)
        EventBus.onSync<ClientEvents.ClientDisconnectedEvent> { _ ->
            AudioPlayerManager.closeAllBlocking(true)
        }

        EventBus.on<ClientEvents.ClientDisconnectedEvent> { _ ->
            ProcessLifecycleManager.shutdownAll()
            ClientCoroutineScope.reset()
        }

        EventBus.onSync<CommonEvents.LevelUnloadEvent> { event ->
            if (event.side == LogicalSide.CLIENT) {
                AudioPlayerManager.closeAllBlocking(true)
                ClientCoroutineScope.reset()
            }
        }

        // Dedicated/integrated server stops
        EventBus.on<ServerEvents.ServerStoppedEvent> { _ ->
            ServerCoroutineScope.reset()
        }

        // Game is fully closing
        EventBus.on<CommonEvents.GameShuttingDownEvent> { event ->
            if (event.side == LogicalSide.CLIENT) {
                AudioPlayerManager.closeAll(true)
                ProcessLifecycleManager.shutdownAll()
                ClientCoroutineScope.reset()
            }
            ServerCoroutineScope.reset()
        }
    }
}
