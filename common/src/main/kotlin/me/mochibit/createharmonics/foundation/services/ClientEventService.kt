package me.mochibit.createharmonics.foundation.services

import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.Connection

interface ClientEventService {
    fun onClientTick(tickPhase: EventPhase, listener: () -> Unit)

    // client game server
    fun onClientDisconnect(
        listener: (
                controller: MultiPlayerGameMode?,
                localPlayer: LocalPlayer?,
                networkManager: Connection?
                ) -> Unit
    )

    //gui

    fun onScreenInit(
        initPhase: EventPhase,
        listener: (
                screen: Screen,
                listenerList: List<GuiEventListener>,
                addListener: (GuiEventListener) -> Unit,
                removeListener: (GuiEventListener) -> Unit
                ) -> Unit
    )
}

val clientEventService: ClientEventService by lazy { loadService<ClientEventService>() }

enum class EventPhase {
    START,
    END,
}

