package me.mochibit.createharmonics.gui.core

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

/**
 * Defines an area in absolute coordinates that have can handle clicks and have tooltips.
 */
class InteractiveRegion(
    val bounds: Rect,
    val clip: Rect? = null,
    val tooltip: Component? = null,
    val onClick: ((mouseX: Double, mouseY: Double) -> Boolean)? = null,
) {
    fun hits(x: Double, y: Double): Boolean =
        bounds.contains(x, y) && (clip?.contains(x, y) ?: true)
}

class InteractionRegistry {
    private val regions = mutableListOf<InteractiveRegion>()

    fun begin() = regions.clear()

    fun register(region: InteractiveRegion) {
        regions.add(region)
    }

    fun handleClick(mouseX: Double, mouseY: Double): Boolean {
        for (region in regions.asReversed()) {
            val cb = region.onClick ?: continue
            if (region.hits(mouseX, mouseY)) return cb(mouseX, mouseY)
        }
        return false
    }

    fun renderHoveredTooltip(graphics: GuiGraphics, font: Font, mouseX: Int, mouseY: Int) {
        val region = regions.asReversed().firstOrNull {
            it.tooltip != null && it.hits(mouseX.toDouble(), mouseY.toDouble())
        } ?: return
        graphics.renderTooltip(font, font.split(region.tooltip!!, 200), mouseX, mouseY)
    }
}
