package me.mochibit.createharmonics.foundation.eventbus

import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.ClientPlayerNetworkEvent
import net.minecraftforge.client.event.ScreenEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.Event
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.fml.DistExecutor
import kotlin.reflect.KClass

object ForgeClientEventBridge : ClientEventBridge<Event>() {
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
