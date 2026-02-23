package me.mochibit.createharmonics.extension

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence

fun Component.toMultilineComponent(): List<Component> {
    val text = this.string
    if (!text.contains("\n")) {
        return listOf(this)
    }

    return text.split("\n").map { line ->
        Component.literal(line).withStyle(this.style)
    }
}

fun Component.toMultilineFormattedCharSequence(
    font: Font,
    maxWidth: Int,
): List<FormattedCharSequence> = font.split(this, maxWidth)

fun GuiGraphics.renderTooltip(
    font: Font,
    lines: List<Component>,
    x: Int,
    y: Int,
    maxWidth: Int = 200,
) {
    val formattedLines = lines.flatMap { it.toMultilineFormattedCharSequence(font, maxWidth) }
    this.renderTooltip(font, formattedLines, x, y)
}

/**
 * Convenience method for rendering a single component as a tooltip,
 * automatically splitting on newlines.
 */
fun GuiGraphics.renderTooltip(
    font: Font,
    component: Component,
    x: Int,
    y: Int,
    maxWidth: Int = 200,
) {
    this.renderTooltip(font, component.toMultilineComponent(), x, y, maxWidth)
}

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
