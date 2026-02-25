package me.mochibit.createharmonics.foundation.async

import dev.architectury.event.events.common.LifecycleEvent
import dev.architectury.platform.Platform
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import me.mochibit.createharmonics.foundation.err
import net.minecraft.client.Minecraft
import net.minecraft.server.MinecraftServer
import kotlin.coroutines.CoroutineContext

object ModDispatchers {
    private var currentServer: MinecraftServer? = null

    init {
        LifecycleEvent.SERVER_STARTED.register { server ->
            currentServer = server
        }

        LifecycleEvent.SERVER_STOPPED.register {
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
