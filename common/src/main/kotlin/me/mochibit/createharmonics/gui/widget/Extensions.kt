package me.mochibit.createharmonics.gui.widget

import net.createmod.catnip.gui.widget.AbstractSimiWidget
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor


data class TooltipStyle(
    val header: Int = AbstractSimiWidget.HEADER_RGB.rgb,
    val body: Int = ChatFormatting.GRAY.color!!,
    val headerLines: Int = 0,
) {
    companion object {
        val Default = TooltipStyle(headerLines = 0, body = ChatFormatting.WHITE.color!!)
        val SimpleHeader = TooltipStyle(headerLines = 1)
    }
}

fun Collection<Component>.styled(style: TooltipStyle = TooltipStyle.Default): List<Component> =
    mapIndexed { i, line ->
        val color = if (i < style.headerLines) style.header else style.body
        line.copy().withStyle { s -> s.withColor(color) }
    }

fun AbstractSimiWidget.applyTooltip(lines: Collection<Component>, style: TooltipStyle = TooltipStyle.Default) {
    toolTip.clear()
    toolTip.addAll(lines.styled(style))
}