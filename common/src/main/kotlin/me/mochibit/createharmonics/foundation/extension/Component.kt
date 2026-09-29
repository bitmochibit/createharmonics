package me.mochibit.createharmonics.foundation.extension

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

fun GuiGraphics.renderMultilineTooltip(
    font: Font,
    component: Component,
    x: Int,
    y: Int,
    maxWidth: Int = 200,
) = renderTooltip(font, font.split(component, maxWidth), x, y)

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
