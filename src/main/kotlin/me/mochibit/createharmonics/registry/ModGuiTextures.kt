package me.mochibit.createharmonics.registry

import com.simibubi.create.Create
import me.mochibit.createharmonics.CreateHarmonicsMod
import net.createmod.catnip.gui.TextureSheetSegment
import net.createmod.catnip.gui.UIRenderHelper
import net.createmod.catnip.gui.element.ScreenElement
import net.createmod.catnip.theme.Color
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn

enum class ModGuiTextures(
    namespace: String,
    location: String?,
    private val startX: Int,
    private val startY: Int,
    private val width: Int,
    private val height: Int,
) : ScreenElement,
    TextureSheetSegment {
    RECORD_PRESS_BASE("record_press_base", 181, 102),
    RECORD_PRESS_BASE_LINK_ICON("record_press_base", 0, 109, 18, 18),
    RECORD_PRESS_BASE_NAME_ICON("record_press_base", 21, 109, 18, 18),
    RECORD_PRESS_BASE_INPUT("record_press_base", 42, 109, 111, 18),
    ;

    private val _location: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(namespace, "textures/gui/$location.png")

    constructor(location: String?, width: Int, height: Int) : this(location, 0, 0, width, height)

    constructor(location: String?, startX: Int, startY: Int, width: Int, height: Int) : this(
        CreateHarmonicsMod.MOD_ID,
        location,
        startX,
        startY,
        width,
        height,
    )

    override fun getLocation(): ResourceLocation = _location

    @OnlyIn(Dist.CLIENT)
    override fun render(
        graphics: GuiGraphics,
        x: Int,
        y: Int,
    ) {
        graphics.blit(location, x, y, startX, startY, width, height)
    }

    @OnlyIn(Dist.CLIENT)
    fun render(
        graphics: GuiGraphics,
        x: Int,
        y: Int,
        c: Color,
    ) {
        bind()
        UIRenderHelper.drawColoredTexture(graphics, c, x, y, startX, startY, width, height)
    }

    override fun getStartX(): Int = startX

    override fun getStartY(): Int = startY

    override fun getWidth(): Int = width

    override fun getHeight(): Int = height

    companion object {
        const val FONT_COLOR: Int = 0x575F7A
    }
}
