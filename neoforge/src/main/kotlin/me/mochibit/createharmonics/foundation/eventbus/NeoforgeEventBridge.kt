package me.mochibit.createharmonics.foundation.eventbus

import net.minecraft.server.level.ServerPlayer
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.Event
import net.neoforged.bus.api.EventPriority
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.GameShuttingDownEvent
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.level.LevelEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import kotlin.reflect.KClass

object NeoforgeEventBridge : PlatformEventBridge<Event>() {
    override fun <FE : Event> registerListener(
        klass: KClass<FE>,
        mapper: FE.() -> ServerProxyEvent,
    ) {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, klass.java) { event: FE ->
            EventBus.post(event.mapper())
        }
    }

    override fun <FE : Event> registerClientListener(
        klass: KClass<FE>,
        mapper: FE.() -> ClientProxyEvent,
    ) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, klass.java) { event: FE ->
                EventBus.post(event.mapper())
            }
        }
    }

    override fun setupProxyEvents() {
        on<ServerStartedEvent>()
            .register { ServerEvents.ServerStartedEvent(server) }
        on<ServerStoppedEvent>()
            .register { ServerEvents.ServerStoppedEvent(server) }
        on<EntityJoinLevelEvent>()
            .registerBoth { CommonEvents.EntityJoinLevelEvent(entity, level) }
        on<PlayerEvent.StartTracking>()
            .register { ServerEvents.PlayerStartTrackingEntity(entity as ServerPlayer, target) }
        on<RegisterCommandsEvent>()
            .registerBoth { CommonEvents.RegisterCommandsEvent(dispatcher, commandSelection, buildContext) }
        on<LevelEvent.Unload>()
            .registerBoth { CommonEvents.LevelUnloadEvent(level) }
        on<GameShuttingDownEvent>()
            .registerBoth { CommonEvents.GameShuttingDownEvent() }

        on<ClientPlayerNetworkEvent.LoggingOut>()
            .registerClient { ClientEvents.ClientDisconnectedEvent(multiPlayerGameMode, player, connection) }
        on<ClientTickEvent.Pre>()
            .registerClient {
                TickEvents.ClientTickEvent(type = TickEvents.Type.CLIENT, phase = TickEvents.Phase.START)
            }
        on<ClientTickEvent.Post>()
            .registerClient {
                TickEvents.ClientTickEvent(type = TickEvents.Type.CLIENT, phase = TickEvents.Phase.END)
            }

        on<ScreenEvent.Init.Post>().registerClient {
            ClientEvents.ScreenEvent.Init(
                this.screen,
                this.listenersList,
                this::addListener,
                this::removeListener,
            )
        }
    }
}
