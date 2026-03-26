package me.mochibit.createharmonics.content.processing.recordPressBase

import net.createmod.catnip.gui.ScreenOpener

object ClientHandler {
    fun openRecordPressScreen(be: RecordPressBaseBlockEntity) {
        ScreenOpener.open(RecordPressBaseScreen(be))
    }
}
