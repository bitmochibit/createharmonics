package me.mochibit.createharmonics.foundation.gui

import me.mochibit.createharmonics.CreateHarmonicsMod
import net.createmod.catnip.gui.TextureSheetSegment
import net.createmod.catnip.gui.UIRenderHelper
import net.createmod.catnip.gui.element.ScreenElement
import net.createmod.catnip.theme.Color
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn

data class ModGuiTexture(
    val textureName: String,
    private val startX: Int,
    private val startY: Int,
    private val width: Int,
    private val height: Int,
    private val namespace: String = CreateHarmonicsMod.MOD_ID,
) : ScreenElement,
    TextureSheetSegment {
    @OnlyIn(Dist.CLIENT)
    override fun render(
        graphics: GuiGraphics,
        x: Int,
        y: Int,
    ) {
        graphics.blit(location, x, y, startX, startY, width, height)
    }

    @OnlyIn(Dist.CLIENT)
    fun renderColored(
        graphics: GuiGraphics,
        x: Int,
        y: Int,
        color: Color,
    ) {
        bind()
        UIRenderHelper.drawColoredTexture(graphics, color, x, y, startX, startY, width, height)
    }

    override fun getStartX(): Int = startX

    override fun getStartY(): Int = startY

    override fun getWidth(): Int = width

    override fun getHeight(): Int = height

    override fun getLocation(): ResourceLocation = ResourceLocation.fromNamespaceAndPath(namespace, "textures/gui/$textureName.png")
}
