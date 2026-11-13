package me.mochibit.createharmonics.content.uploader

import me.mochibit.createharmonics.gui.content.amethystCatalyst.AmethystCatalystScreen
import net.createmod.catnip.gui.ScreenOpener

/**
 * Client handler
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new ClientHandler
 */
object ClientHandler {
    /**
     * Starts the amethyst catalyst screen
     *
     * @param be
     */
    fun openAmethystCatalystScreen(be: AmethystCatalystBlockEntity) {
        ScreenOpener.open(AmethystCatalystScreen(be))
    }
}