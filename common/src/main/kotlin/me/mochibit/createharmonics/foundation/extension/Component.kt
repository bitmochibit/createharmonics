package me.mochibit.createharmonics.foundation.extension

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence

/**
 * Converts the multiline components on the receiver [Component]
 *
 * @return the components
 */
fun Component.toMultilineComponent(): List<Component> {
    val text = this.string
    if (!text.contains("\n")) {
        return listOf(this)
    }

    return text.split("\n").map { line ->
        Component.literal(line).withStyle(this.style)
    }
}

/**
 * Displays the multiline tooltip on the receiver [GuiGraphics]
 *
 * @param font
 * @param component
 * @param x
 * @param y
 * @param maxWidth the max width
 */
fun GuiGraphics.renderMultilineTooltip(
    font: Font,
    component: Component,
    x: Int,
    y: Int,
    maxWidth: Int = 200,
) = renderTooltip(font, font.split(component, maxWidth), x, y)

/**
 * Displays the centered string on the receiver [GuiGraphics]
 *
 * @param font
 * @param text
 * @param x
 * @param y
 * @param color
 * @param maxWidth the max width
 */
fun GuiGraphics.drawCenteredString(
    font: Font,
    text: Component,
    x: Int,
    y: Int,
    color: Int,
    maxWidth: Int = 200,
) {
    val formattedText = font.split(text, maxWidth)
    formattedText.forEach {
        this.drawCenteredString(font, it, x, y, color)
    }
}
