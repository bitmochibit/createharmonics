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
    var onHoverIcon: (() -> ModGuiTexture)? = null,
    var onClickIcon: (() -> ModGuiTexture)? = null,
    var onDisabledIcon: (() -> ModGuiTexture)? = null,
    var iconOffsetX: Int = 0,
    var iconOffsetY: Int = 0,
) : IconButton(x, y, width, height, icon) {
    override fun doRender(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTicks: Float,
    ) {
        if (visible) {
            isHovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height
            val icon =
                when {
                    !active -> onDisabledIcon?.invoke() ?: this.icon
                    isHovered && AllKeys.isMouseButtonDown(0) -> onClickIcon?.invoke() ?: this.icon
                    isHovered -> onHoverIcon?.invoke() ?: this.icon
                    green -> AllGuiTextures.BUTTON_GREEN
                    else -> this.icon
                }

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
            drawBackground(graphics, icon)
            icon.render(graphics, x + iconOffsetX, y + iconOffsetY)
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

            else -> {
                throw IllegalArgumentException("Unsupported button type: ${button::class.java}")
            }
        }
    }
}
