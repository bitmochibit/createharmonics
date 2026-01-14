package me.mochibit.createharmonics.foundation.gui.widget

import com.mojang.blaze3d.systems.RenderSystem
import com.simibubi.create.AllKeys
import com.simibubi.create.foundation.gui.AllGuiTextures
import com.simibubi.create.foundation.gui.widget.IconButton
import me.mochibit.createharmonics.foundation.gui.ModGuiTexture
import net.createmod.catnip.gui.element.ScreenElement
import net.minecraft.client.gui.GuiGraphics

class AdvancedIconButton(
    x: Int,
    y: Int,
    icon: ScreenElement,
    width: Int = 18,
    height: Int = 18,
    var onHoverBg: (() -> ModGuiTexture)? = null,
    var onClickBg: (() -> ModGuiTexture)? = null,
    var onDisabledBg: (() -> ModGuiTexture)? = null,
    var iconOffsetX: Int = 1,
    var iconOffsetY: Int = 1,
    var zIndex: Int = 0,
    var toolTipZIndex: Int = 0,
    var defaultButton: Boolean = true,
) : IconButton(x, y, width, height, icon) {
    override fun doRender(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTicks: Float,
    ) {
        if (zIndex != 0) {
            graphics.pose().translate(0.0f, 0.0f, zIndex.toFloat())
        }

        isHovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height
        val button =
            when {
                !active -> {
                    onDisabledBg?.invoke() ?: if (defaultButton) AllGuiTextures.BUTTON_DISABLED else null
                }

                isHovered && AllKeys.isMouseButtonDown(0) -> {
                    onClickBg?.invoke()
                        ?: if (defaultButton) AllGuiTextures.BUTTON_DOWN else null
                }

                isHovered -> {
                    onHoverBg?.invoke() ?: if (defaultButton) AllGuiTextures.BUTTON_HOVER else null
                }

                green -> {
                    AllGuiTextures.BUTTON_GREEN
                }

                else -> {
                    if (defaultButton) AllGuiTextures.BUTTON else null
                }
            }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
        button?.let { drawBackground(graphics, it) }
        icon.render(graphics, x + iconOffsetX, y + iconOffsetY)

        if (zIndex != 0) {
            graphics.pose().translate(0.0f, 0.0f, -zIndex.toFloat())
        }
    }

    override fun renderTooltip(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTicks: Float,
    ) {
        if (toolTipZIndex != 0) {
            graphics.pose().translate(0.0f, 0.0f, toolTipZIndex.toFloat())
        }
        super.renderTooltip(graphics, mouseX, mouseY, partialTicks)

        if (toolTipZIndex != 0) {
            graphics.pose().translate(0.0f, 0.0f, -toolTipZIndex.toFloat())
        }
    }

    fun drawBackground(
        graphics: GuiGraphics,
        button: ScreenElement,
    ) {
        when (button) {
            is AllGuiTextures -> {
                this.drawBg(graphics, button)
            }

            is ModGuiTexture -> {
                graphics.blit(
                    button.location,
                    x,
                    y,
                    button.getStartX(),
                    button.getStartY(),
                    button.getWidth(),
                    button.getHeight(),
                )
            }

            else -> {}
        }
    }
}
