package me.mochibit.createharmonics.gui.core

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

/**
 * Defines an area in absolute coordinates that have can handle clicks and have tooltips.
 *
 * @constructor Creates a new InteractiveRegion
 * @property bounds
 * @property clip
 * @property tooltip
 * @property tooltipLines the tooltip lines
 * @property onClick the on click
 * @property onScroll the on scroll
 */
class InteractiveRegion(
    val bounds: Rect,
    val clip: Rect? = null,
    val tooltip: Component? = null,
    val tooltipLines: (() -> List<Component>)? = null,
    val onClick: ((mouseX: Double, mouseY: Double) -> Boolean)? = null,
    val onScroll: ((mouseX: Double, mouseY: Double, delta: Double) -> Boolean)? = null,
) {
    /**
     * Hits
     *
     * @param x
     * @param y
     * @return `true` if the condition holds, `false` otherwise
     */
    fun hits(x: Double, y: Double): Boolean =
        bounds.contains(x, y) && (clip?.contains(x, y) ?: true)
}

/**
 * Interaction registry
 *
 * @constructor Creates a new InteractionRegistry
 */
class InteractionRegistry {
    private val regions = mutableListOf<InteractiveRegion>()

    /**
     * Starts
     *
     */
    fun begin() = regions.clear()

    /**
     * Adds
     *
     * @param region
     */
    fun register(region: InteractiveRegion) {
        regions.add(region)
    }

    /**
     * Handles the click
     *
     * @param mouseX the mouse x
     * @param mouseY the mouse y
     * @return `true` if the condition holds, `false` otherwise
     */
    fun handleClick(mouseX: Double, mouseY: Double): Boolean {
        for (region in regions.asReversed()) {
            val cb = region.onClick ?: continue
            if (region.hits(mouseX, mouseY)) return cb(mouseX, mouseY)
        }
        return false
    }

    /**
     * Handles the scroll
     *
     * @param mouseX the mouse x
     * @param mouseY the mouse y
     * @param delta
     * @return `true` if the condition holds, `false` otherwise
     */
    fun handleScroll(mouseX: Double, mouseY: Double, delta: Double): Boolean =
        regions.asReversed().any { r ->
            r.onScroll != null && r.hits(mouseX, mouseY) && r.onScroll.invoke(mouseX, mouseY, delta)
        }

    /**
     * Displays the hovered tooltip
     *
     * @param graphics
     * @param font
     * @param mouseX the mouse x
     * @param mouseY the mouse y
     */
    fun renderHoveredTooltip(graphics: GuiGraphics, font: Font, mouseX: Int, mouseY: Int) {
        val mx = mouseX.toDouble()
        val my = mouseY.toDouble()
        val region = regions.asReversed().firstOrNull {
            (it.tooltip != null || it.tooltipLines != null) && it.hits(mx, my)
        } ?: return

        region.tooltipLines?.let { supplier ->
            val lines = supplier()
            if (lines.isNotEmpty()) graphics.renderComponentTooltip(font, lines, mouseX, mouseY)
            return
        }
        graphics.renderTooltip(font, font.split(region.tooltip!!, 200), mouseX, mouseY)
    }
}
