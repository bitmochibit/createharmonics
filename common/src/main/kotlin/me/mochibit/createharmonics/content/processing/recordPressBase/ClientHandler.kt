package me.mochibit.createharmonics.content.processing.recordPressBase

import me.mochibit.createharmonics.gui.content.recordPressBase.RecordPressBaseScreen
import net.createmod.catnip.gui.ScreenOpener

/**
 * Client handler
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new ClientHandler
 */
object ClientHandler {
    /**
     * Starts the record press screen
     *
     * @param be
     */
    fun openRecordPressScreen(be: RecordPressBaseBlockEntity) {
        ScreenOpener.open(RecordPressBaseScreen(be))
    }
}
