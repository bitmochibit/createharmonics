package me.mochibit.createharmonics.foundation.services

import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.Connection
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.common.NeoForge

class NeoforgeClientEventService : ClientEventService {
    override fun onClientTick(
        tickPhase: EventPhase,
        listener: () -> Unit
    ) {
        if (tickPhase == EventPhase.START) {
            NeoForge.EVENT_BUS.addListener<ClientTickEvent.Pre> { e ->
                listener()
            }
        } else {
            NeoForge.EVENT_BUS.addListener<ClientTickEvent.Post> { e ->
                listener()
            }
        }
    }

    override fun onClientDisconnect(listener: (controller: MultiPlayerGameMode?, localPlayer: LocalPlayer?, networkManager: Connection?) -> Unit) {
        NeoForge.EVENT_BUS.addListener<ClientPlayerNetworkEvent.LoggingOut> { e ->
            listener(e.multiPlayerGameMode, e.player, e.connection)
        }
    }

    override fun onScreenInit(
        initPhase: EventPhase,
        listener: (screen: Screen, listenerList: List<GuiEventListener>, addListener: (GuiEventListener) -> Unit, removeListener: (GuiEventListener) -> Unit) -> Unit
    ) {
        if (initPhase == EventPhase.START) {
            NeoForge.EVENT_BUS.addListener<ScreenEvent.Init.Pre> { e ->
                listener(e.screen, e.listenersList, e::addListener, e::removeListener)
            }
        } else {
            NeoForge.EVENT_BUS.addListener<ScreenEvent.Init.Post> { e ->
                listener(e.screen, e.listenersList, e::addListener, e::removeListener)
            }
        }
    }

}

