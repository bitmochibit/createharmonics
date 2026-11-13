package me.mochibit.createharmonics.foundation.services

import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.Connection

/**
 * Client event service
 *
 */
interface ClientEventService {
    /**
     * On client tick
     *
     * @param tickPhase the tick phase
     * @param listener
     * @receiver
     */
    fun onClientTick(tickPhase: EventPhase, listener: () -> Unit)

    /**
     * On client disconnect
     *
     * @param listener
     * @receiver
     */// client game server
    fun onClientDisconnect(
        listener: (
                controller: MultiPlayerGameMode?,
                localPlayer: LocalPlayer?,
                networkManager: Connection?
                ) -> Unit
    )

    //gui

    /**
     * On screen init
     *
     * @param initPhase the init phase
     * @param listener
     * @receiver
     */
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

/**
 * Event phase
 * Enum — a fixed set of named constants.
 *
 * @constructor Creates a new EventPhase
 */
enum class EventPhase {
    /**
     * Starts
     *
     * @constructor Creates a new START
     */
    START,

    /**
     * Stops
     *
     * @constructor Creates a new END
     */
    END,
}

