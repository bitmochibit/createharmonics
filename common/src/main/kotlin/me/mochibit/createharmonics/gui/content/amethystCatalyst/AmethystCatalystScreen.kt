package me.mochibit.createharmonics.gui.content.amethystCatalyst

import me.mochibit.createharmonics.foundation.locale.ModLang
import me.mochibit.createharmonics.gui.ModGuiTexture
import net.createmod.catnip.gui.AbstractSimiScreen
import net.minecraft.client.gui.GuiGraphics

class AmethystCatalystScreen: AbstractSimiScreen(
    ModLang.translate("gui.amethyst_catalyst.title").component()
) {

    val background = ModGuiTexture("amethyst_catalyst",
        0,0, 234, 176)

    private inner class AudioEntry()

    override fun renderWindow(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTicks: Float
    ) {
        TODO("Not yet implemented")
    }
}