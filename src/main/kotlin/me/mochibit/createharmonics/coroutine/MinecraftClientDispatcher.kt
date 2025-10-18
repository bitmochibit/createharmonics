package me.mochibit.createharmonics.coroutine

import kotlinx.coroutines.CoroutineDispatcher
import me.mochibit.createharmonics.Logger
import net.minecraft.client.Minecraft
import kotlin.coroutines.CoroutineContext

object MinecraftClientDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        try {
            Minecraft.getInstance().execute(block)

        } catch (e: Exception) {
            Logger.err("Error dispatching to Minecraft main thread")
        }
    }
}


