package me.mochibit.createharmonics.gui.widget

import net.createmod.catnip.gui.widget.AbstractSimiWidget
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor


/**
 * Tooltip style
 * Data class — a value holder; `equals`, `hashCode`, `toString` and `copy` are generated.
 *
 * @constructor Creates a new TooltipStyle
 * @property header
 * @property body
 * @property headerLines the header lines
 */
data class TooltipStyle(
    val header: Int = AbstractSimiWidget.HEADER_RGB.rgb,
    val body: Int = ChatFormatting.GRAY.color!!,
    val headerLines: Int = 0,
) {
    /**
     * Companion
     * Companion object — holds factory methods and constants for the enclosing class.
     *
     * @constructor Creates a new Companion
     */
    companion object {
        val Default = TooltipStyle(headerLines = 0, body = ChatFormatting.WHITE.color!!)
        val SimpleHeader = TooltipStyle(headerLines = 1)
    }
}

/**
 * Styled on the receiver [Collection<Component>]
 *
 * @param style
 * @return the components
 */
fun Collection<Component>.styled(style: TooltipStyle = TooltipStyle.Default): List<Component> =
    mapIndexed { i, line ->
        val color = if (i < style.headerLines) style.header else style.body
        line.copy().withStyle { s -> s.withColor(color) }
    }

/**
 * Handles the tooltip on the receiver [AbstractSimiWidget]
 *
 * @param lines
 * @param style
 */
fun AbstractSimiWidget.applyTooltip(lines: Collection<Component>, style: TooltipStyle = TooltipStyle.Default) {
    toolTip.clear()
    toolTip.addAll(lines.styled(style))
}