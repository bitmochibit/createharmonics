package me.mochibit.createharmonics.content.block.andesiteJukebox

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory

class AndesiteJukeboxScreen(
    menu: AndesiteJukeboxMenu,
    playerInventory: Inventory,
    title: Component
) : AbstractContainerScreen<AndesiteJukeboxMenu>(menu, playerInventory, title) {

    companion object {
        private val TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "createharmonics",
            "textures/gui/andesite_jukebox.png"
        )
    }

    init {
        this.imageWidth = 176
        this.imageHeight = 166
    }

    override fun renderBg(guiGraphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
        val x = (width - imageWidth) / 2
        val y = (height - imageHeight) / 2
        guiGraphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics)
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        renderTooltip(guiGraphics, mouseX, mouseY)
    }
}

