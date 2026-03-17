package me.mochibit.createharmonics.lifecycle

import me.mochibit.createharmonics.audio.process.ProcessLifecycleManager
import me.mochibit.createharmonics.foundation.info
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.server.ServerStartingEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod.EventBusSubscriber
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent

/**
 * Handles server lifecycle events for the mod.
 */
@EventBusSubscriber(bus = EventBusSubscriber.Bus.FORGE)
object ServerLifecycleHandler {
    @JvmStatic
    @SubscribeEvent
    fun onServerStarting(event: ServerStartingEvent) {
        "Create: Harmonics server is starting!".info()
    }

    @JvmStatic
    @SubscribeEvent
    fun onServerStopping(event: ServerStoppingEvent) {
        "Create: Harmonics server is stopping, cleaning up processes...".info()
        ProcessLifecycleManager.shutdownAll()
    }
}

/**
 * Handles client lifecycle events for the mod.
 */
@EventBusSubscriber(bus = EventBusSubscriber.Bus.FORGE, value = [Dist.CLIENT])
object ClientLifecycleHandler {
    @JvmStatic
    @SubscribeEvent
    fun onClientDisconnect(event: net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut) {
        "Client disconnecting from world, cleaning up processes...".info()
        ProcessLifecycleManager.shutdownAll()
    }
}

/**
 * Handles client mod bus events.
 */
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
object ClientModBusHandler {
    @SubscribeEvent
    @JvmStatic
    fun onClientSetup(event: FMLClientSetupEvent) {
        "Create: Harmonics client is setting up!".info()
    }
}
