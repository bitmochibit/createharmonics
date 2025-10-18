package me.mochibit.createharmonics.coroutine

import kotlinx.coroutines.CoroutineDispatcher
import me.mochibit.createharmonics.Logger
import net.minecraftforge.server.ServerLifecycleHooks
import kotlin.coroutines.CoroutineContext

object MinecraftServerDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        try {
            ServerLifecycleHooks.getCurrentServer().execute(block)

        } catch (e: Exception) {
            Logger.err("Error dispatching to Minecraft main thread")
        }
    }
}