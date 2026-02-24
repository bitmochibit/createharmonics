package me.mochibit.createharmonics.foundation.async

import dev.architectury.platform.Platform
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import me.mochibit.createharmonics.foundation.err
import net.minecraft.client.Minecraft
import net.minecraftforge.server.ServerLifecycleHooks
import kotlin.coroutines.CoroutineContext

object ModDispatchers {
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
                ServerLifecycleHooks.getCurrentServer().execute(block)
            } catch (e: Exception) {
                "Error dispatching to Minecraft main thread".err()
            }
        }
    }
}
