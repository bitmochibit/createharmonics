package me.mochibit.createharmonics.foundation.eventbus

import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.GameShuttingDownEvent
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.event.entity.EntityJoinLevelEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.level.LevelEvent
import net.minecraftforge.event.server.ServerStartedEvent
import net.minecraftforge.event.server.ServerStoppedEvent
import net.minecraftforge.eventbus.api.Event
import net.minecraftforge.eventbus.api.EventPriority
import kotlin.reflect.KClass

object ForgeEventBridge : CommonEventBridge<Event>() {
    override fun <FE : Event> registerListener(
        klass: KClass<FE>,
        mapper: FE.() -> ServerProxyEvent,
    ) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, klass.java) { event: FE ->
            EventBus.post(event.mapper())
        }
    }

    override fun setupProxyEvents() {
        on<ServerStartedEvent>()
            .register { ServerEvents.ServerStartedEvent(server) }
        on<ServerStoppedEvent>()
            .register { ServerEvents.ServerStoppedEvent(server) }
        on<EntityJoinLevelEvent>()
            .registerBoth { side -> CommonEvents.EntityJoinLevelEvent(entity, level, side) }
        on<PlayerEvent.StartTracking>()
            .register { ServerEvents.PlayerStartTrackingEntity(entity as ServerPlayer, target) }
        on<PlayerEvent.StopTracking>()
            .register { ServerEvents.PlayerStopTrackingEntity(entity as ServerPlayer, target) }
        on<RegisterCommandsEvent>()
            .registerBoth { side -> CommonEvents.RegisterCommandsEvent(dispatcher, commandSelection, buildContext, side) }
        on<LevelEvent.Unload>()
            .registerBoth { side -> CommonEvents.LevelUnloadEvent(level, side) }
        on<GameShuttingDownEvent>()
            .registerBoth { side -> CommonEvents.GameShuttingDownEvent(side) }
    }
}
