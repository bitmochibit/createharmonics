package me.mochibit.createharmonics.foundation.async

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import me.mochibit.createharmonics.event.lifecycle.ServerStartedEvent
import me.mochibit.createharmonics.event.lifecycle.ServerStoppedEvent
import me.mochibit.createharmonics.foundation.err
import me.mochibit.createharmonics.foundation.eventbus.EventBus
import net.minecraft.client.Minecraft
import net.minecraft.server.MinecraftServer
import kotlin.coroutines.CoroutineContext

object ModDispatchers {
    private var currentServer: MinecraftServer? = null

    init {
        EventBus.on<ServerStartedEvent> { event ->
            currentServer = event.server
        }
        EventBus.on<ServerStoppedEvent> { event ->
            currentServer = null
        }
    }

    class Client : CoroutineDispatcher() {
        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            try {
                Minecraft.getInstance().execute(block)
            } catch (e: Exception) {
                "Error dispatching to Minecraft main thread".err()
            }
        }
    }

    class Server : CoroutineDispatcher() {
        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            try {
                currentServer?.execute(block)
            } catch (e: Exception) {
                "Error dispatching to Minecraft main thread".err()
            }
        }
    }
}
