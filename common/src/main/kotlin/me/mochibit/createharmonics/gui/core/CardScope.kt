package me.mochibit.createharmonics.gui.core

import me.mochibit.createharmonics.gui.widget.EntryScrollInput
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

/**
 * Card scope
 *
 * @constructor Creates a new CardScope
 * @property graphics
 * @property registry
 * @property baseX the base x
 * @property baseY the base y
 * @property absoluteBaseX the absolute base x
 * @property absoluteBaseY the absolute base y
 * @property scrollOffsetInt the scroll offset int
 * @property clip
 */
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
    private fun localX(x: Double) = x - (absoluteBaseX - baseX)
    private fun localY(y: Double) = y - (absoluteBaseY - baseY) - scrollOffsetInt

    /**
     * Button
     *
     * @param localX the local x
     * @param localY the local y
     * @param size
     * @param tooltip
     * @param draw
     * @receiver
     * @param onClick the on click
     * @receiver
     * @param z
     */
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

    /**
     * Edit box
     *
     * @param widget
     * @param localX the local x
     * @param localY the local y
     * @param width
     * @param height
     * @param mouseX the mouse x
     * @param mouseY the mouse y
     * @param partialTicks the partial ticks
     * @param tooltip
     * @param z
     */
    fun editBox(
        widget: EditBox,
        localX: Int,
        localY: Int,
        width: Int,
        height: Int,
        mouseX: Int,
        mouseY: Int,
        partialTicks: Float,
        tooltip: List<Component>? = null,
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
                tooltipLines = tooltip?.let { lines -> { lines } },
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

    /**
     * Scroll input
     *
     * @param widget
     * @param localX the local x
     * @param localY the local y
     * @param width
     * @param height
     * @param mouseX the mouse x
     * @param mouseY the mouse y
     * @param partialTicks the partial ticks
     * @param tooltip
     * @param z
     */
    fun scrollInput(
        widget: EntryScrollInput,
        localX: Int, localY: Int, width: Int, height: Int,
        mouseX: Int, mouseY: Int, partialTicks: Float,
        tooltip: Component? = null,
        z: Float = 0f,
    ) {
        widget.x = baseX + localX
        widget.y = baseY + localY
        widget.setWidth(width)
        graphics.layer(z) { widget.render(this@layer, mouseX, mouseY, partialTicks) }
        registry.register(
            InteractiveRegion(
                bounds = Rect(absoluteBaseX + localX, absoluteBaseY + localY + scrollOffsetInt, width, height),
                clip = clip,
                tooltip = tooltip,
                tooltipLines = { if (widget.active) widget.hoverTooltip else emptyList() },
                onClick = { cx, cy -> widget.mouseClicked(localX(cx), localY(cy), 0); true },
                onScroll = { cx, cy, delta -> widget.mouseScrolled(localX(cx), localY(cy), 0.0, delta) },
            ),
        )
    }

    /**
     * Decoration
     *
     * @param localX the local x
     * @param localY the local y
     * @param z
     * @param draw
     * @receiver
     */
    fun decoration(localX: Int, localY: Int, z: Float = 0f, draw: GuiGraphics.(x: Int, y: Int) -> Unit) {
        graphics.layer(z) { draw(baseX + localX, baseY + localY) }
    }
}

