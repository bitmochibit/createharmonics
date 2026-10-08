package me.mochibit.createharmonics.content.uploader

import me.mochibit.createharmonics.gui.content.amethystCatalyst.AmethystCatalystScreen
import net.createmod.catnip.gui.ScreenOpener

object ClientHandler {
    fun openAmethystCatalystScreen(be: AmethystCatalystBlockEntity) {
        ScreenOpener.open(AmethystCatalystScreen(be))
    }
}