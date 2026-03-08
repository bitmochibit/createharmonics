package me.mochibit.createharmonics.foundation.eventbus

import net.minecraftforge.client.event.ClientPlayerNetworkEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.GameShuttingDownEvent
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.event.level.LevelEvent
import net.minecraftforge.event.server.ServerStartedEvent
import net.minecraftforge.event.server.ServerStoppedEvent
import net.minecraftforge.eventbus.api.Event
import net.minecraftforge.eventbus.api.EventPriority

object ForgeEventBridge : PlatformEventBridge() {
    private inline fun <reified FE : Event, reified PE : ProxyEvent> proxy(crossinline mapper: FE.() -> PE) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, FE::class.java) { event: FE -> EventBus.post(event.mapper()) }
        registeredProxyTypes += PE::class
    }

    override fun setupProxyEvents() {
        proxy<ServerStartedEvent, ProxyEvent.ServerStartedEventProxy> { ProxyEvent.ServerStartedEventProxy(server) }
        proxy<ServerStoppedEvent, ProxyEvent.ServerStoppedEventProxy> { ProxyEvent.ServerStoppedEventProxy(server) }
        proxy<ClientPlayerNetworkEvent.LoggingOut, ProxyEvent.ClientDisconnectedEventProxy> {
            ProxyEvent.ClientDisconnectedEventProxy(multiPlayerGameMode, player, connection)
        }
        proxy<RegisterCommandsEvent, ProxyEvent.RegisterCommandsEventProxy> {
            ProxyEvent.RegisterCommandsEventProxy(dispatcher, commandSelection, buildContext)
        }
        proxy<LevelEvent.Unload, ProxyEvent.LevelUnloadEventProxy> { ProxyEvent.LevelUnloadEventProxy(level) }
        proxy<GameShuttingDownEvent, ProxyEvent.GameShuttingDownEventProxy> { ProxyEvent.GameShuttingDownEventProxy() }
    }
}
