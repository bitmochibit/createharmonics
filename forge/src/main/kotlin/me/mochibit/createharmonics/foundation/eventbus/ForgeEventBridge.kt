package me.mochibit.createharmonics.foundation.eventbus

import me.mochibit.createharmonics.foundation.info
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.ClientPlayerNetworkEvent
import net.minecraftforge.client.event.ScreenEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.GameShuttingDownEvent
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.EntityJoinLevelEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.level.LevelEvent
import net.minecraftforge.event.server.ServerStartedEvent
import net.minecraftforge.event.server.ServerStoppedEvent
import net.minecraftforge.eventbus.api.Event
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.fml.DistExecutor
import kotlin.reflect.KClass

object ForgeEventBridge : PlatformEventBridge<Event>() {
    override fun <FE : Event> registerListener(
        klass: KClass<FE>,
        mapper: FE.() -> ServerProxyEvent,
    ) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, klass.java) { event: FE ->
            EventBus.post(event.mapper())
        }
    }

    override fun <FE : Event> registerClientListener(
        klass: KClass<FE>,
        mapper: FE.() -> ClientProxyEvent,
    ) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT) {
            Runnable {
                MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, klass.java) { event: FE ->
                    EventBus.post(event.mapper())
                }
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
        on<TickEvent.ClientTickEvent>()
            .registerClient {
                TickEvents.ClientTickEvent(
                    TickEvents.Type.valueOf(type.name),
                    TickEvents.Phase.valueOf(phase.name),
                )
            }
        on<ScreenEvent.Init>().registerClient {
            ClientEvents.ScreenEvent.Init(
                this.screen,
                this.listenersList,
                this::addListener,
                this::removeListener,
            )
        }
    }
}
