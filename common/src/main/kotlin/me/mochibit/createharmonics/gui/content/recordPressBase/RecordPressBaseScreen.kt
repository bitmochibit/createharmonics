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
    }

    private val background = ModGuiTexture("record_press_base", 0, 0, 234, 176)

    private val pointerTexture = ModGuiTexture("record_press_base", 185, 239, 21, 16)
    private val pointerOffscreenTexture = ModGuiTexture("record_press_base", 171, 244, 13, 6)
    private val randomModeTexture = ModGuiTexture("record_press_base", 224, 240, 16, 16)
    private val sequentialModeTexture = ModGuiTexture("record_press_base", 224, 224, 16, 16)

    private val noteStripTexture = ModGuiTexture("record_press_base", 13, 237, 9, 18)
    private val linkArrowTextureLeft = ModGuiTexture("record_press_base", 112, 239, 10, 16)
    private val percentageLabelTextureLeft = ModGuiTexture("record_press_base", 114, 221, 8, 16)


    private val renderedItem = ItemStack(ModBlocks.RECORD_PRESS_BASE.get())

    data class Configuration(
        val urls: MutableList<String> = mutableListOf(),
        val weights: MutableList<Float> = mutableListOf(),
        var randomMode: Boolean = false,
        var currentUrlIndex: Int = 0,
    )

    private val configuration = Configuration()

    private val scissorGuard = ScissorGuard()
    private val interactions = InteractionRegistry()
    private lateinit var scrollArea: ScrollArea

    private val urlBoxes = mutableListOf<EditBox>()
    private val weightBoxes = mutableListOf<EditBox>()

    private lateinit var confirmButton: IconButton
    private lateinit var modeButton: IconButton
    private lateinit var increaseIndexButton: IconButton
    private lateinit var decreaseIndexButton: IconButton
    private var changedIndexOnce = false

    private val cardTops = ArrayList<Int>()

    private fun urlInputWidth() = if (configuration.randomMode) URL_FIELD_WIDTH_RANDOM else URL_FIELD_WIDTH_SEQUENTIAL

    override fun init() {
        setWindowSize(background.width, background.height)
        super.init()
        clearWidgets()

        if (configuration.urls.isEmpty()) {
            configuration.urls.addAll(be.audioUrls)
            configuration.weights.addAll(be.urlWeights)
            configuration.randomMode = be.randomMode
            configuration.currentUrlIndex = be.currentUrlIndex
            while (configuration.weights.size < configuration.urls.size) configuration.weights.add(1f)
        }

        scrollArea = ScrollArea(
            bounds = Rect(guiLeft + SCROLL_AREA_X, guiTop + SCROLL_AREA_Y, SCROLL_AREA_WIDTH, SCROLL_AREA_HEIGHT),
            scissorGuard = scissorGuard
        )

        initButtons()
        rebuildEditBoxes()
    }

    private fun initButtons() {
        confirmButton = AdvancedIconButton(
            guiLeft + background.width - 33,
            guiTop + background.height - 24,
            AllIcons.I_CONFIRM,
        ).apply {
            withCallback<IconButton> { minecraft?.player?.closeContainer() }
            zIndex = 100
            toolTipZIndex = 6000
            addRenderableWidget(this)
        }

        modeButton = AdvancedIconButton(
            guiLeft + 10,
            guiTop + background.height - 24,
            if (configuration.randomMode) randomModeTexture else sequentialModeTexture,
        ).apply {
            withCallback<IconButton> {
                configuration.randomMode = !configuration.randomMode
                setIcon(if (configuration.randomMode) randomModeTexture else sequentialModeTexture)
                updateModeTooltip()
                rebuildEditBoxes()
            }
            toolTipZIndex = 6000
            zIndex = 100
            addRenderableWidget(this)
        }
        updateModeTooltip()

        increaseIndexButton =
            AdvancedIconButton(guiLeft + 50, guiTop + background.height - 24, AllIcons.I_PRIORITY_LOW).apply {
                withCallback<IconButton> { advanceIndex(+1) }
                zIndex = 100
                toolTipZIndex = 6000
                setToolTip(ModLang.translate("gui.record_press_base.url_index_increase").component())
                addRenderableWidget(this)
            }

        decreaseIndexButton =
            AdvancedIconButton(guiLeft + 70, guiTop + background.height - 24, AllIcons.I_PRIORITY_HIGH).apply {
                withCallback<IconButton> { advanceIndex(-1) }
                zIndex = 100
                toolTipZIndex = 6000
                setToolTip(ModLang.translate("gui.record_press_base.url_index_decrease").component())
                addRenderableWidget(this)
            }
    }

    private fun advanceIndex(delta: Int) {
        if (configuration.urls.isEmpty()) return
        changedIndexOnce = true
        val n = configuration.urls.size
        configuration.currentUrlIndex = (configuration.currentUrlIndex + delta + n) % n
    }

    private fun updateModeTooltip() {
        modeButton.toolTip.clear()
        val text = if (configuration.randomMode) {
            ModLang.translate("gui.record_press_base.url_random_mode").component()
        } else {
            ModLang.translate("gui.record_press_base.url_sequential_mode").component()
        }
        modeButton.toolTip.addAll(text.toMultilineComponent())
    }

    private fun rebuildEditBoxes() {
        urlBoxes.clear()
        weightBoxes.clear()
        while (configuration.weights.size < configuration.urls.size) configuration.weights.add(1f)
        while (configuration.weights.size > configuration.urls.size) configuration.weights.removeAt(configuration.weights.size - 1)

        configuration.urls.forEachIndexed { index, url ->
            val width = urlInputWidth()
            urlBoxes.add(
                EditBox(
                    font,
                    0,
                    0,
                    width,
                    URL_FIELD_HEIGHT,
                    ModLang.translate("gui.record_press_base.url_input").component()
                ).apply {
                    value = url
                    setWidth(width)
                    setBordered(false)
                    setMaxLength(2048)
                    setResponder { configuration.urls[index] = it }
                },
            )
            weightBoxes.add(
                EditBox(
                    font,
                    0,
                    0,
                    30,
                    URL_FIELD_HEIGHT,
                    ModLang.translate("gui.record_press_base.weight_input").component()
                ).apply {
                    value = String.format("%.2f", configuration.weights.getOrElse(index) { 1f })
                    setBordered(false)
                    setMaxLength(6)
                    setResponder { newValue ->
                        val parsed = newValue.toFloatOrNull() ?: return@setResponder
                        val clamped = parsed.coerceIn(0f, 1f)
                        configuration.weights[index] = clamped
                        if (parsed != clamped) value = String.format("%.2f", clamped)
                    }
                },
            )
        }
    }

    override fun renderWindow(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        val x = guiLeft
        val y = guiTop
        background.render(graphics, x, y)
        graphics.drawString(font, title, x + background.width / 2 - font.width(title) / 2, y + 4, 0x592424, false)


        interactions.begin()

        UIRenderHelper.drawStretched(
            graphics,
            x + 33,
            y + SCROLL_AREA_Y,
            3,
            SCROLL_AREA_HEIGHT,
            0,
            AllGuiTextures.SCHEDULE_STRIP_DARK,
        )


        scrollArea.render(graphics, mouseX, mouseY, partialTicks) {
            val entries = configuration.urls
            cardTops.clear()
            var cy = 9

            UIRenderHelper.drawStretched(graphics, 30,0, 3, 10, 0, AllGuiTextures.SCHEDULE_STRIP_LIGHT)

            entries.forEachIndexed { i, _ ->
                cardTops += cy
                cy += graphics.renderCard(i, cy, this)
                if (i < entries.lastIndex) {
                    layer(0f) {
                        AllGuiTextures.SCHEDULE_STRIP_DOTTED.render(
                            graphics,
                            26,
                            cy - 3
                        )
                    }
                    cy += CARD_SPACING
                }
            }

            if (entries.isNotEmpty()) cy += 9
            graphics.renderInsertRow(22, cy, this)

            cy + 20
        }

        graphics.fillGradient(
            x + SCROLL_AREA_X,
            y + SCROLL_AREA_Y,
            x + SCROLL_AREA_X + SCROLL_AREA_WIDTH,
            y + SCROLL_AREA_Y + 10,
            300,
            0x77000000,
            0x00000000,
        )

        graphics.fillGradient(
            x + SCROLL_AREA_X,
            y + SCROLL_AREA_Y + SCROLL_AREA_HEIGHT - 10,
            x + SCROLL_AREA_X + SCROLL_AREA_WIDTH,
            y + SCROLL_AREA_Y + SCROLL_AREA_HEIGHT,
            300,
            0x00000000,
            0x77000000,
        )

        renderSelectionPointer(graphics)

        GuiGameElement.of(renderedItem)
            .at<GuiGameElement.GuiRenderBuilder>(x + background.width + 6f, y + background.height - 56f, -200f)
            .scale(5.0)
            .render(graphics)

        interactions.renderHoveredTooltip(graphics, font, mouseX, mouseY)
    }


    private fun GuiGraphics.renderCard(index: Int, y: Int, area: ScrollScope): Int {
        val x = 20
        val mouseX = area.mouseX
        val mouseY = area.mouseY
        val partialTicks = area.partialTicks
        val cardWidth = CARD_WIDTH
        val cardHeight = CARD_HEADER_HEIGHT + CARD_PADDING
        val width = urlInputWidth()
        val scope = CardScope(
            this, interactions,
            baseX = x, baseY = y,
            absoluteBaseX = area.originX + x,
            absoluteBaseY = area.originY + y,
            scrollOffsetInt = area.scrollOffset.toInt(),
            clip = area.clip
        )

        scope.decoration(0, 0) { gx, gy -> renderCardBackground(gx, gy, cardWidth, cardHeight) }


        scope.button(
            localX = cardWidth - 14,
            localY = 2,
            tooltip = ModLang.translate("gui.record_press_base.url_remove").component(),
            draw = { gx, gy -> AllGuiTextures.SCHEDULE_CARD_REMOVE.render(this, gx, gy) },
            onClick = { removeUrlEntry(index) },
            z = 2f
        )
        if (index > 0) {
            scope.button(
                localX = cardWidth,
                localY = -4,
                tooltip = ModLang.translate("gui.record_press_base.url_move_up").component(),
                draw = { gx, gy -> AllGuiTextures.SCHEDULE_CARD_MOVE_UP.render(this, gx, gy) },
                onClick = { swapUrlEntries(index, index - 1) },
                z = 2f
            )
        }
        if (index < configuration.urls.size - 1) {
            scope.button(
                localX = cardWidth,
                localY = CARD_HEADER_HEIGHT-4,
                tooltip = ModLang.translate("gui.record_press_base.url_move_down").component(),
                draw = { gx, gy -> AllGuiTextures.SCHEDULE_CARD_MOVE_DOWN.render(this, gx, gy) },
                onClick = { swapUrlEntries(index, index + 1) },
                z = 2f
            )
        }

        val inputX = 28
        val inputY = 8
        scope.decoration(0, 0, 2f) { x, y ->
            UIRenderHelper.drawStretched(this, x+10, y-10, 3, cardHeight+20, 0, AllGuiTextures.SCHEDULE_STRIP_LIGHT)
            noteStripTexture.render(this, x+7, y+CARD_SPACING)

            UIRenderHelper.drawStretched(
                this,
                x + inputX,
                y + inputY,
                width,
                URL_FIELD_HEIGHT,
                0,
                AllGuiTextures.SCHEDULE_CONDITION_MIDDLE
            )
            linkArrowTextureLeft.render(this, x + inputX - 10, y + inputY)
            AllGuiTextures.SCHEDULE_CONDITION_RIGHT.render(this, x + inputX + width, y + inputY)
        }

        if (index < urlBoxes.size) {
            val box = urlBoxes[index]
            if (!box.isFocused) {
                val currentUrl = configuration.urls.getOrNull(index) ?: ""
                if (box.value != currentUrl) box.value = currentUrl
            }
            (box as AbstractWidgetAccessor).setHeight(URL_FIELD_HEIGHT)
            scope.editBox(
                widget = box,
                localX = inputX,
                localY = inputY + 4,
                width = width,
                height = URL_FIELD_HEIGHT,
                mouseX = mouseX,
                mouseY = mouseY,
                partialTicks = partialTicks,
                tooltip = tooltipForUrl(index),
                z = 4f
            )

            if (configuration.randomMode && index < weightBoxes.size) {
                val weightBox = weightBoxes[index]
                val weightX = inputX + width + 18
                scope.decoration(0, 0, z = 3f) { _, _ ->
                    UIRenderHelper.drawStretched(
                        this,
                        x + weightX,
                        y + inputY,
                        35,
                        URL_FIELD_HEIGHT,
                        0,
                        AllGuiTextures.SCHEDULE_CONDITION_MIDDLE
                    )
                    percentageLabelTextureLeft.render(this, x + weightX - 8, y + inputY)
                    AllGuiTextures.SCHEDULE_CONDITION_RIGHT.render(this, x + weightX + 35, y + inputY)
                }
                (weightBox as AbstractWidgetAccessor).setHeight(URL_FIELD_HEIGHT)
                scope.editBox(
                    widget = weightBox,
                    localX = weightX + 2,
                    localY = inputY + 4,
                    width = 30,
                    height = URL_FIELD_HEIGHT,
                    mouseX = mouseX,
                    mouseY = mouseY,
                    partialTicks = partialTicks,
                    tooltip = ModLang.translate("gui.record_press_base.weight_input_tooltip").component(),
                    z=4f
                )
            }
        }

        return cardHeight
    }

    private fun GuiGraphics.renderCardBackground(x: Int, y: Int, cardWidth: Int, cardHeight: Int) {
        val zLevel = 1
        poseScoped {
            UIRenderHelper.drawStretched(
                this,
                x,
                y + 1,
                cardWidth,
                cardHeight - 2,
                zLevel,
                AllGuiTextures.SCHEDULE_CARD_LIGHT
            )
            UIRenderHelper.drawStretched(
                this,
                x + 1,
                y,
                cardWidth - 2,
                cardHeight,
                zLevel,
                AllGuiTextures.SCHEDULE_CARD_LIGHT
            )
            UIRenderHelper.drawStretched(
                this,
                x + 1,
                y + 1,
                cardWidth - 2,
                cardHeight - 2,
                zLevel,
                AllGuiTextures.SCHEDULE_CARD_DARK
            )
            UIRenderHelper.drawStretched(
                this,
                x+2,
                y+2,
                cardWidth - 4,
                cardHeight - 4,
                zLevel,
                AllGuiTextures.SCHEDULE_CARD_MEDIUM
            )
        }
    }

    private fun GuiGraphics.renderInsertRow(x: Int, y: Int, area: ScrollScope) {
        AllGuiTextures.SCHEDULE_STRIP_END.render(this, x + 4, y)
        val scope = CardScope(
            this, interactions,
            baseX = x, baseY = y,
            absoluteBaseX = area.originX + x,
            absoluteBaseY = area.originY + y,
            scrollOffsetInt = area.scrollOffset.toInt(),
            clip = area.clip
        )
        scope.button(
            localX = 24,
            localY = 0,
            tooltip = ModLang.translate("gui.record_press_base.url_add").component(),
            draw = { gx, gy -> ModGuiTexture("record_press_base", 79, 239, 16, 16).render(this, gx, gy) },
            onClick = {
                configuration.urls.add("")
                configuration.weights.add(1f)
                rebuildEditBoxes()
            },
        )
    }

    private fun tooltipForUrl(index: Int) =
        ModLang.translate("gui.record_press_base.url_input_tooltip").component()


    private fun renderSelectionPointer(graphics: GuiGraphics) {
        if (configuration.urls.isEmpty() || configuration.currentUrlIndex >= configuration.urls.size) return
        val top = cardTops.getOrNull(configuration.currentUrlIndex) ?: return
        val expectedY = (scrollArea.toScreenY(top) + 6).toFloat()
        val actualY = Mth.clamp(
            expectedY,
            (guiTop + SCROLL_AREA_Y).toFloat(),
            (guiTop + SCROLL_AREA_Y + SCROLL_AREA_HEIGHT - 15).toFloat()
        )
        graphics.poseScoped(translateY = actualY, translateZ = 300f) {
            val texture = if (expectedY == actualY) {
                pointerTexture
            } else {
                pointerOffscreenTexture
            }
            texture.render(this, guiLeft - 14, 0)
        }
    }

    private fun removeUrlEntry(index: Int) {
        if (index !in configuration.urls.indices) return
        configuration.urls.removeAt(index)
        if (index < configuration.weights.size) configuration.weights.removeAt(index)
        configuration.currentUrlIndex = when {
            configuration.urls.isEmpty() -> 0
            configuration.currentUrlIndex >= configuration.urls.size -> configuration.urls.size - 1
            configuration.currentUrlIndex > index -> configuration.currentUrlIndex - 1
            else -> configuration.currentUrlIndex
        }
        rebuildEditBoxes()
    }

    private fun swapUrlEntries(a: Int, b: Int) {
        if (a !in configuration.urls.indices || b !in configuration.urls.indices) return
        configuration.urls[a] = configuration.urls[b].also { configuration.urls[b] = configuration.urls[a] }
        if (a < configuration.weights.size && b < configuration.weights.size) {
            configuration.weights[a] =
                configuration.weights[b].also { configuration.weights[b] = configuration.weights[a] }
        }
        configuration.currentUrlIndex = when (configuration.currentUrlIndex) {
            a -> b
            b -> a
            else -> configuration.currentUrlIndex
        }
        rebuildEditBoxes()
    }

    override fun tick() {
        scrollArea.tick()
        if (be.currentUrlIndex != configuration.currentUrlIndex && !changedIndexOnce) {
            configuration.currentUrlIndex = be.currentUrlIndex
        }
        super.tick()
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        scrollArea.mouseScrolled(mouseX, mouseY, scrollY)
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        (urlBoxes + weightBoxes).forEach { it.isFocused = false }
        if (interactions.handleClick(mouseX, mouseY)) return true
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        (urlBoxes + weightBoxes).forEach { box ->
            if (box.isFocused && box.keyPressed(keyCode, scanCode, modifiers)) return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        (urlBoxes + weightBoxes).forEach { box ->
            if (box.isFocused && box.charTyped(codePoint, modifiers)) return true
        }
        return super.charTyped(codePoint, modifiers)
    }

    override fun removed() {
        val safeIndex = if (configuration.urls.isEmpty()) 0 else configuration.currentUrlIndex.coerceIn(
            0,
            configuration.urls.size - 1
        )
        ModPackets.sendToServer(
            ConfigureRecordPressBasePacket(
                be.blockPos,
                configuration.urls,
                configuration.weights,
                configuration.randomMode,
                safeIndex
            ),
        )
    }
}