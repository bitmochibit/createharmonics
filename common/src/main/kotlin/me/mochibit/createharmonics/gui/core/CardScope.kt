package me.mochibit.createharmonics.gui.core

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

class CardScope(
    private val graphics: GuiGraphics,
    private val registry: InteractionRegistry,
    private val baseX: Int,
    private val baseY: Int,
    private val absoluteBaseX: Int,
    private val absoluteBaseY: Int,
    private val scrollOffsetInt: Int,
    val clip: Rect
) {
    fun button(
        localX: Int,
        localY: Int,
        size: Int = 16,
        tooltip: Component? = null,
        draw: GuiGraphics.(x: Int, y: Int) -> Unit,
        onClick: () -> Unit,
        z: Float = 0f
    ) {
        graphics.layer(z) {draw(graphics, baseX + localX, baseY + localY)}
        registry.register(
            InteractiveRegion(
                bounds = Rect(absoluteBaseX + localX, absoluteBaseY + localY + scrollOffsetInt, size, size),
                clip = clip,
                tooltip = tooltip,
                onClick = { _, _ -> onClick(); true },
            ),
        )
    }

    fun editBox(
        widget: EditBox,
        localX: Int,
        localY: Int,
        width: Int,
        height: Int,
        mouseX: Int,
        mouseY: Int,
        partialTicks: Float,
        tooltip: Component? = null,
        z: Float = 0f
    ) {
        widget.x = baseX + localX
        widget.y = baseY + localY
        graphics.layer(z) {
            widget.render(this@layer, mouseX, mouseY, partialTicks)
        }
        registry.register(
            InteractiveRegion(
                bounds = Rect(absoluteBaseX + localX, absoluteBaseY + localY + scrollOffsetInt, width, height),
                clip = clip,
                tooltip = tooltip,
                onClick = { clickX, clickY ->
                    widget.isFocused = true
                    widget.onClick(
                        clickX - (absoluteBaseX - baseX),
                        clickY - (absoluteBaseY - baseY) - scrollOffsetInt,
                    )
                    true
                }
            ),
        )
    }

    fun decoration(localX: Int, localY: Int, z: Float = 0f, draw: GuiGraphics.(x: Int, y: Int) -> Unit) {
        graphics.layer(z) { draw(baseX + localX, baseY + localY) }
    }
}

