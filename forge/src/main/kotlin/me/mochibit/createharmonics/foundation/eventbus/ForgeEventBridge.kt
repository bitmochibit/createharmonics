package me.mochibit.createharmonics.foundation.eventbus

import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerPlayerConnection
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.client.event.ClientPlayerNetworkEvent
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

object ForgeEventBridge : PlatformEventBridge() {
    private inline fun <reified FE : Event, reified PE : ProxyEvent> proxy(crossinline mapper: FE.() -> PE) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, FE::class.java) { event: FE -> EventBus.post(event.mapper()) }
    }

    override fun setupProxyEvents() {
        ProxyEventType.entries.forEach { type ->
            @Suppress("UNUSED_VARIABLE")
            val enforced: Unit =
                when (type) {
                    ProxyEventType.SERVER_STARTED -> {
                        proxy<ServerStartedEvent, ProxyEvent.ServerStartedEventProxy> { ProxyEvent.ServerStartedEventProxy(server) }
                    }

                    ProxyEventType.SERVER_STOPPED -> {
                        proxy<ServerStoppedEvent, ProxyEvent.ServerStoppedEventProxy> { ProxyEvent.ServerStoppedEventProxy(server) }
                    }

                    ProxyEventType.ENTITY_JOIN_LEVEL -> {
                        proxy<EntityJoinLevelEvent, ProxyEvent.EntityJoinLevelEventProxy> {
                            ProxyEvent.EntityJoinLevelEventProxy(
                                entity,
                                level,
                            )
                        }
                    }

                    ProxyEventType.PLAYER_START_TRACKING -> {
                        proxy<PlayerEvent.StartTracking, ProxyEvent.PlayerStartTrackingEntityProxy> {
                            ProxyEvent.PlayerStartTrackingEntityProxy(entity as ServerPlayer, target)
                        }
                    }

                    ProxyEventType.REGISTER_COMMANDS -> {
                        proxy<RegisterCommandsEvent, ProxyEvent.RegisterCommandsEventProxy> {
                            ProxyEvent.RegisterCommandsEventProxy(dispatcher, commandSelection, buildContext)
                        }
                    }

                    ProxyEventType.LEVEL_UNLOAD -> {
                        proxy<LevelEvent.Unload, ProxyEvent.LevelUnloadEventProxy> { ProxyEvent.LevelUnloadEventProxy(level) }
                    }

                    ProxyEventType.GAME_SHUTTING_DOWN -> {
                        proxy<GameShuttingDownEvent, ProxyEvent.GameShuttingDownEventProxy> { ProxyEvent.GameShuttingDownEventProxy() }
                    }

                    ProxyEventType.CLIENT_DISCONNECTED -> {
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT) {
                            Runnable {
                                proxy<ClientPlayerNetworkEvent.LoggingOut, ProxyEvent.ClientDisconnectedEventProxy> {
                                    ProxyEvent.ClientDisconnectedEventProxy(multiPlayerGameMode, player, connection)
                                }
                            }
                        }
                    }

                    ProxyEventType.CLIENT_TICK -> {
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT) {
                            Runnable {
                                proxy<TickEvent.ClientTickEvent, ProxyEvent.TickEvent.ClientTickEventProxy> {
                                    ProxyEvent.TickEvent.ClientTickEventProxy(
                                        ProxyEvent.TickEvent.Type.valueOf(this.type.name),
                                        ProxyEvent.TickEvent.Phase.valueOf(this.phase.name),
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }
}
