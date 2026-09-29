package me.mochibit.createharmonics.gui.content.recordPressBase

import com.simibubi.create.foundation.gui.AllGuiTextures
import com.simibubi.create.foundation.gui.AllIcons
import com.simibubi.create.foundation.gui.widget.IconButton
import me.mochibit.createharmonics.content.processing.recordPressBase.RecordPressBaseBlockEntity
import me.mochibit.createharmonics.foundation.extension.toMultilineComponent
import me.mochibit.createharmonics.foundation.locale.ModLang
import me.mochibit.createharmonics.foundation.network.packet.ConfigureRecordPressBasePacket
import me.mochibit.createharmonics.foundation.registry.ModBlocks
import me.mochibit.createharmonics.foundation.registry.ModPackets
import me.mochibit.createharmonics.gui.ModGuiTexture
import me.mochibit.createharmonics.gui.core.*
import me.mochibit.createharmonics.gui.widget.AdvancedIconButton
import me.mochibit.createharmonics.mixin.AbstractWidgetAccessor
import net.createmod.catnip.gui.AbstractSimiScreen
import net.createmod.catnip.gui.UIRenderHelper
import net.createmod.catnip.gui.element.GuiGameElement
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import java.util.Collections
import java.util.Locale

class RecordPressBaseScreen(
    private val be: RecordPressBaseBlockEntity,
) : AbstractSimiScreen(ModLang.translate("gui.record_press_base.title").component()) {

    companion object {
        private const val SCROLL_AREA_X = 3
        private const val SCROLL_AREA_Y = 16
        private const val SCROLL_AREA_WIDTH = 220
        private const val SCROLL_AREA_HEIGHT = 129
        private const val CARD_WIDTH = 180
        private const val CARD_HEADER_HEIGHT = 30
        private const val CARD_PADDING = 4
        private const val CARD_SPACING = 10
        private const val URL_FIELD_WIDTH_RANDOM = 80
        private const val URL_FIELD_WIDTH_SEQUENTIAL = 130
        private const val URL_FIELD_HEIGHT = 16
        private const val WEIGHT_FIELD_WIDTH = 30
    }

    // textures
    private val background = ModGuiTexture("record_press_base", 0, 0, 234, 176)
    private val pointerTexture = ModGuiTexture("record_press_base", 185, 239, 21, 16)
    private val pointerOffscreenTexture = ModGuiTexture("record_press_base", 171, 244, 13, 6)
    private val randomModeTexture = ModGuiTexture("record_press_base", 224, 240, 16, 16)
    private val sequentialModeTexture = ModGuiTexture("record_press_base", 224, 224, 16, 16)
    private val addTexture = ModGuiTexture("record_press_base", 79, 239, 16, 16)
    private val noteStripTexture = ModGuiTexture("record_press_base", 13, 237, 9, 18)
    private val linkArrowTextureLeft = ModGuiTexture("record_press_base", 112, 239, 10, 16)
    private val percentageLabelTextureLeft = ModGuiTexture("record_press_base", 114, 221, 8, 16)

    private val renderedItem = ItemStack(ModBlocks.RECORD_PRESS_BASE.get())

    // model
    private inner class Entry(var url: String = "", weight: Float = 1f) {
        var weight: Float = weight.coerceIn(0f, 1f)

        val urlBox: EditBox by lazy {
            newEditBox("url_input", maxLength = 2048) {
                value = this@Entry.url
                setResponder { this@Entry.url = it }
            }
        }

        val weightBox: EditBox by lazy {
            newEditBox("weight_input", maxLength = 6) {
                value = this@Entry.weight.formatted()
                setResponder { text ->
                    text.toFloatOrNull()?.let { this@Entry.weight = it.coerceIn(0f, 1f) }
                }
            }
        }

        val boxes: Sequence<EditBox> get() = sequenceOf(urlBox, weightBox)
    }

    private fun newEditBox(langKey: String, maxLength: Int, configure: EditBox.() -> Unit) =
        EditBox(
            font, 0, 0, 0, URL_FIELD_HEIGHT,
            ModLang.translate("gui.record_press_base.$langKey").component(),
        ).apply {
            setBordered(false)
            setMaxLength(maxLength)
            configure()
        }

    private fun Float.formatted() = "%.2f".format(Locale.ROOT, this)

    private fun EditBox.applyWidth(w: Int) {
        if (width != w) {
            setWidth(w)
            moveCursorToStart(false)
        }
    }

    private val entries: MutableList<Entry> =
        be.audioUrls.mapIndexedTo(mutableListOf()) { i, url -> Entry(url, be.urlWeights.getOrElse(i) { 1f }) }

    private var randomMode = be.randomMode
    private var current: Entry? = entries.getOrNull(be.currentUrlIndex)
    private var selectionTouched = false

    // state

    private val scissorGuard = ScissorGuard()
    private val interactions = InteractionRegistry()
    private lateinit var scrollArea: ScrollArea
    private lateinit var modeButton: IconButton

    private val cardTops = ArrayList<Int>()

    private val allBoxes get() = entries.asSequence().flatMap { it.boxes }
    private fun urlInputWidth() = if (randomMode) URL_FIELD_WIDTH_RANDOM else URL_FIELD_WIDTH_SEQUENTIAL

    // operations

    private fun addEntry() {
        entries += Entry()
    }

    private fun removeEntry(entry: Entry) {
        val i = entries.indexOf(entry).takeIf { it >= 0 } ?: return
        entries.removeAt(i)
        selectionTouched = true
        if (current === entry) current = entries.getOrNull(i) ?: entries.lastOrNull()
    }

    private fun moveEntry(entry: Entry, delta: Int) {
        val from = entries.indexOf(entry)
        val to = from + delta
        if (from < 0 || to !in entries.indices) return
        selectionTouched = true
        Collections.swap(entries, from, to)
    }

    private fun advanceSelection(delta: Int) {
        if (entries.isEmpty()) return
        selectionTouched = true
        val from = entries.indexOf(current).coerceAtLeast(0)
        current = entries[(from + delta).mod(entries.size)]
    }

    private fun toggleMode() {
        randomMode = !randomMode
        modeButton.setIcon(if (randomMode) randomModeTexture else sequentialModeTexture)
        updateModeTooltip()
    }

    private fun updateModeTooltip() {
        val key = if (randomMode) "url_random_mode" else "url_sequential_mode"
        modeButton.toolTip.clear()
        modeButton.toolTip.addAll(ModLang.translate("gui.record_press_base.$key").component().toMultilineComponent())
    }

    // init

    override fun init() {
        setWindowSize(background.width, background.height)
        super.init()
        clearWidgets()

        scrollArea = ScrollArea(
            bounds = Rect(guiLeft + SCROLL_AREA_X, guiTop + SCROLL_AREA_Y, SCROLL_AREA_WIDTH, SCROLL_AREA_HEIGHT),
            scissorGuard = scissorGuard,
        )

        initButtons()
    }

    private fun initButtons() {
        val buttonY = guiTop + background.height - 24

        AdvancedIconButton(guiLeft + background.width - 33, buttonY, AllIcons.I_CONFIRM).apply {
            withCallback<IconButton> { minecraft?.player?.closeContainer() }
            zIndex = 100
            toolTipZIndex = 6000
            addRenderableWidget(this)
        }

        modeButton = AdvancedIconButton(
            guiLeft + 10, buttonY,
            if (randomMode) randomModeTexture else sequentialModeTexture,
        ).apply {
            withCallback<IconButton> { toggleMode() }
            zIndex = 100
            toolTipZIndex = 6000
            addRenderableWidget(this)
        }
        updateModeTooltip()

        AdvancedIconButton(guiLeft + 50, buttonY, AllIcons.I_PRIORITY_LOW).apply {
            withCallback<IconButton> { advanceSelection(+1) }
            zIndex = 100
            toolTipZIndex = 6000
            setToolTip(ModLang.translate("gui.record_press_base.url_index_increase").component())
            addRenderableWidget(this)
        }

        AdvancedIconButton(guiLeft + 70, buttonY, AllIcons.I_PRIORITY_HIGH).apply {
            withCallback<IconButton> { advanceSelection(-1) }
            zIndex = 100
            toolTipZIndex = 6000
            setToolTip(ModLang.translate("gui.record_press_base.url_index_decrease").component())
            addRenderableWidget(this)
        }
    }

    // render

    override fun renderWindow(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        val x = guiLeft
        val y = guiTop
        background.render(graphics, x, y)
        graphics.drawString(font, title, x + background.width / 2 - font.width(title) / 2, y + 4, 0x592424, false)

        interactions.begin()

        UIRenderHelper.drawStretched(
            graphics, x + 33, y + SCROLL_AREA_Y, 3, SCROLL_AREA_HEIGHT, 0, AllGuiTextures.SCHEDULE_STRIP_DARK,
        )

        scrollArea.render(graphics, mouseX, mouseY, partialTicks) {
            cardTops.clear()
            var cy = 9

            UIRenderHelper.drawStretched(graphics, 30, 0, 3, 10, 0, AllGuiTextures.SCHEDULE_STRIP_LIGHT)

            entries.forEachIndexed { i, entry ->
                cardTops += cy
                cy += graphics.renderCard(entry, i, cy, this)
                if (i < entries.lastIndex) {
                    layer(0f) { AllGuiTextures.SCHEDULE_STRIP_DOTTED.render(graphics, 26, cy - 3) }
                    cy += CARD_SPACING
                }
            }

            if (entries.isNotEmpty()) cy += 9
            graphics.renderInsertRow(22, cy, this)

            cy + 20
        }

        graphics.fillGradient(
            x + SCROLL_AREA_X, y + SCROLL_AREA_Y,
            x + SCROLL_AREA_X + SCROLL_AREA_WIDTH, y + SCROLL_AREA_Y + 10,
            300, 0x77000000, 0x00000000,
        )
        graphics.fillGradient(
            x + SCROLL_AREA_X, y + SCROLL_AREA_Y + SCROLL_AREA_HEIGHT - 10,
            x + SCROLL_AREA_X + SCROLL_AREA_WIDTH, y + SCROLL_AREA_Y + SCROLL_AREA_HEIGHT,
            300, 0x00000000, 0x77000000,
        )

        renderSelectionPointer(graphics)

        GuiGameElement.of(renderedItem)
            .at<GuiGameElement.GuiRenderBuilder>(x + background.width + 6f, y + background.height - 56f, -200f)
            .scale(5.0)
            .render(graphics)

        interactions.renderHoveredTooltip(graphics, font, mouseX, mouseY)
    }

    private fun GuiGraphics.renderCard(entry: Entry, index: Int, y: Int, area: ScrollScope): Int {
        val x = 20
        val cardHeight = CARD_HEADER_HEIGHT + CARD_PADDING
        val width = urlInputWidth()
        val scope = CardScope(
            this, interactions,
            baseX = x, baseY = y,
            absoluteBaseX = area.originX + x,
            absoluteBaseY = area.originY + y,
            scrollOffsetInt = area.scrollOffset.toInt(),
            clip = area.clip,
        )

        scope.decoration(0, 0) { gx, gy -> renderCardBackground(gx, gy, CARD_WIDTH, cardHeight) }

        scope.button(
            localX = CARD_WIDTH - 14,
            localY = 2,
            tooltip = ModLang.translate("gui.record_press_base.url_remove").component(),
            draw = { gx, gy -> AllGuiTextures.SCHEDULE_CARD_REMOVE.render(this, gx, gy) },
            onClick = { removeEntry(entry) },
            z = 2f,
        )
        if (index > 0) {
            scope.button(
                localX = CARD_WIDTH,
                localY = -4,
                tooltip = ModLang.translate("gui.record_press_base.url_move_up").component(),
                draw = { gx, gy -> AllGuiTextures.SCHEDULE_CARD_MOVE_UP.render(this, gx, gy) },
                onClick = { moveEntry(entry, -1) },
                z = 2f,
            )
        }
        if (index < entries.lastIndex) {
            scope.button(
                localX = CARD_WIDTH,
                localY = CARD_HEADER_HEIGHT - 4,
                tooltip = ModLang.translate("gui.record_press_base.url_move_down").component(),
                draw = { gx, gy -> AllGuiTextures.SCHEDULE_CARD_MOVE_DOWN.render(this, gx, gy) },
                onClick = { moveEntry(entry, +1) },
                z = 2f,
            )
        }

        val inputX = 28
        val inputY = 8
        scope.decoration(0, 0, 2f) { dx, dy ->
            UIRenderHelper.drawStretched(this, dx + 10, dy - 10, 3, cardHeight + 20, 0, AllGuiTextures.SCHEDULE_STRIP_LIGHT)
            noteStripTexture.render(this, dx + 7, dy + CARD_SPACING)
            UIRenderHelper.drawStretched(
                this, dx + inputX, dy + inputY, width, URL_FIELD_HEIGHT, 0, AllGuiTextures.SCHEDULE_CONDITION_MIDDLE,
            )
            linkArrowTextureLeft.render(this, dx + inputX - 10, dy + inputY)
            AllGuiTextures.SCHEDULE_CONDITION_RIGHT.render(this, dx + inputX + width, dy + inputY)
        }

        entry.urlBox.let { box ->
            box.applyWidth(width)
            (box as AbstractWidgetAccessor).setHeight(URL_FIELD_HEIGHT)
            scope.editBox(
                widget = box,
                localX = inputX,
                localY = inputY + 4,
                width = width,
                height = URL_FIELD_HEIGHT,
                mouseX = area.mouseX,
                mouseY = area.mouseY,
                partialTicks = area.partialTicks,
                tooltip = ModLang.translate("gui.record_press_base.url_input_tooltip").component(),
                z = 4f,
            )
        }

        if (randomMode) {
            val weightX = inputX + width + 18
            scope.decoration(0, 0, z = 3f) { dx, dy ->
                UIRenderHelper.drawStretched(
                    this, dx + weightX, dy + inputY, 35, URL_FIELD_HEIGHT, 0, AllGuiTextures.SCHEDULE_CONDITION_MIDDLE,
                )
                percentageLabelTextureLeft.render(this, dx + weightX - 8, dy + inputY)
                AllGuiTextures.SCHEDULE_CONDITION_RIGHT.render(this, dx + weightX + 35, dy + inputY)
            }
            entry.weightBox.let { box ->
                box.applyWidth(WEIGHT_FIELD_WIDTH)
                (box as AbstractWidgetAccessor).setHeight(URL_FIELD_HEIGHT)
                scope.editBox(
                    widget = box,
                    localX = weightX + 2,
                    localY = inputY + 4,
                    width = WEIGHT_FIELD_WIDTH,
                    height = URL_FIELD_HEIGHT,
                    mouseX = area.mouseX,
                    mouseY = area.mouseY,
                    partialTicks = area.partialTicks,
                    tooltip = ModLang.translate("gui.record_press_base.weight_input_tooltip").component(),
                    z = 4f,
                )
            }
        }

        return cardHeight
    }

    private fun GuiGraphics.renderCardBackground(x: Int, y: Int, cardWidth: Int, cardHeight: Int) {
        val z = 1
        poseScoped {
            UIRenderHelper.drawStretched(this, x, y + 1, cardWidth, cardHeight - 2, z, AllGuiTextures.SCHEDULE_CARD_LIGHT)
            UIRenderHelper.drawStretched(this, x + 1, y, cardWidth - 2, cardHeight, z, AllGuiTextures.SCHEDULE_CARD_LIGHT)
            UIRenderHelper.drawStretched(this, x + 1, y + 1, cardWidth - 2, cardHeight - 2, z, AllGuiTextures.SCHEDULE_CARD_DARK)
            UIRenderHelper.drawStretched(this, x + 2, y + 2, cardWidth - 4, cardHeight - 4, z, AllGuiTextures.SCHEDULE_CARD_MEDIUM)
        }
    }

    private fun GuiGraphics.renderInsertRow(x: Int, y: Int, area: ScrollScope) {
        AllGuiTextures.SCHEDULE_STRIP_END.render(this, x + 4, y)
        CardScope(
            this, interactions,
            baseX = x, baseY = y,
            absoluteBaseX = area.originX + x,
            absoluteBaseY = area.originY + y,
            scrollOffsetInt = area.scrollOffset.toInt(),
            clip = area.clip,
        ).button(
            localX = 24,
            localY = 0,
            tooltip = ModLang.translate("gui.record_press_base.url_add").component(),
            draw = { gx, gy -> addTexture.render(this, gx, gy) },
            onClick = ::addEntry,
        )
    }

    private fun renderSelectionPointer(graphics: GuiGraphics) {
        val top = cardTops.getOrNull(entries.indexOf(current)) ?: return
        val expectedY = (scrollArea.toScreenY(top) + 6).toFloat()
        val actualY = Mth.clamp(
            expectedY,
            (guiTop + SCROLL_AREA_Y).toFloat(),
            (guiTop + SCROLL_AREA_Y + SCROLL_AREA_HEIGHT - 15).toFloat(),
        )
        graphics.poseScoped(translateY = actualY, translateZ = 300f) {
            val texture = if (expectedY == actualY) pointerTexture else pointerOffscreenTexture
            texture.render(this, guiLeft - 14, 0)
        }
    }

    // input

    override fun tick() {
        scrollArea.tick()
        if (!selectionTouched) current = entries.getOrNull(be.currentUrlIndex)
        super.tick()
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        scrollArea.mouseScrolled(mouseX, mouseY, scrollY)
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        allBoxes.forEach { it.isFocused = false }
        return interactions.handleClick(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean =
        allBoxes.any { it.isFocused && it.keyPressed(keyCode, scanCode, modifiers) } ||
                super.keyPressed(keyCode, scanCode, modifiers)

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean =
        allBoxes.any { it.isFocused && it.charTyped(codePoint, modifiers) } ||
                super.charTyped(codePoint, modifiers)

    override fun removed() {
        ModPackets.sendToServer(
            ConfigureRecordPressBasePacket(
                be.blockPos,
                entries.map { it.url }.toMutableList(),
                entries.map { it.weight }.toMutableList(),
                randomMode,
                entries.indexOf(current).coerceAtLeast(0),
            ),
        )
    }
}