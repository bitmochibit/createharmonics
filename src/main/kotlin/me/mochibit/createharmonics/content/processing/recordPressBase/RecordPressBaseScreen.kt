package me.mochibit.createharmonics.content.processing.recordPressBase

import com.simibubi.create.foundation.gui.AllGuiTextures
import com.simibubi.create.foundation.gui.AllIcons
import com.simibubi.create.foundation.gui.ScreenWithStencils
import com.simibubi.create.foundation.gui.widget.IconButton
import me.mochibit.createharmonics.foundation.gui.ModGuiTexture
import me.mochibit.createharmonics.foundation.gui.widget.AdvancedIconButton
import me.mochibit.createharmonics.network.packet.ConfigureRecordPressBasePacket
import me.mochibit.createharmonics.registry.ModBlocks
import me.mochibit.createharmonics.registry.ModLang
import me.mochibit.createharmonics.registry.ModPackets
import net.createmod.catnip.animation.LerpedFloat
import net.createmod.catnip.animation.LerpedFloat.Chaser
import net.createmod.catnip.gui.AbstractSimiScreen
import net.createmod.catnip.gui.UIRenderHelper
import net.createmod.catnip.gui.element.GuiGameElement
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import kotlin.math.max

class RecordPressBaseScreen(
    val be: RecordPressBaseBlockEntity,
) : AbstractSimiScreen(ModLang.translate("gui.record_press_base.title").component()),
    ScreenWithStencils {
    // Constants
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

    // Textures
    private val background = ModGuiTexture("record_press_base", 0, 0, 234, 176)
    private val pointerTexture = ModGuiTexture("record_press_base", 185, 239, 21, 16)
    private val pointerOffscreenTexture = ModGuiTexture("record_press_base", 171, 244, 13, 6)
    private val addUrlIcon = ModGuiTexture("record_press_base", 79, 239, 16, 16)
    private val youtubeIcon = ModGuiTexture("record_press_base", 79, 239, 16, 16)
    private val anyUrlIcon = ModGuiTexture("record_press_base", 79, 239, 16, 16)

    private val randomModeTexture = ModGuiTexture("record_press_base", 224, 240, 16, 16)
    private val sequentialModeTexture = ModGuiTexture("record_press_base", 224, 224, 16, 16)

    private val noteStripTexture = ModGuiTexture("record_press_base", 13, 237, 9, 18)

    private val linkArrowTextureLeft = ModGuiTexture("record_press_base", 112, 239, 10, 16)
    private val percentageLabelTextureLeft = ModGuiTexture("record_press_base", 114, 221, 8, 16)

    // UI Components
    private lateinit var confirmButton: IconButton
    private lateinit var insertButton: IconButton
    private lateinit var modeButton: IconButton
    private lateinit var increaseIndexButton: IconButton
    private lateinit var decreaseIndexButton: IconButton
    private val urlInputFields = mutableListOf<EditBox>()
    private val weightInputFields = mutableListOf<EditBox>()

    // State
    private val scroll = LerpedFloat.linear().startWithValue(0.0)
    private val renderedItem = ItemStack(ModBlocks.RECORD_PRESS_BASE.get())

    data class Configuration(
        val urls: MutableList<String> = mutableListOf(),
        val weights: MutableList<Float> = mutableListOf(),
        var randomMode: Boolean = false,
        var currentUrlIndex: Int = 0,
    )

    val configuration = Configuration()

    private var totalContentHeight = 0

    // Track positions for interaction handling
    private data class WidgetPosition(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val scrolledY: Int, // Y position with scroll applied
    )

    private val editBoxPositions = mutableMapOf<Int, WidgetPosition>()
    private var insertButtonPosition: WidgetPosition? = null

    // Track card button positions for interaction
    private data class CardButtonPositions(
        val removeButton: WidgetPosition?,
        val moveUpButton: WidgetPosition?,
        val moveDownButton: WidgetPosition?,
    )

    private val cardButtonPositions = mutableMapOf<Int, CardButtonPositions>()

    private fun getUrlInputWidth() = if (configuration.randomMode) URL_FIELD_WIDTH_RANDOM else URL_FIELD_WIDTH_SEQUENTIAL

    override fun init() {
        setWindowSize(background.width, background.height)
        super.init()
        clearWidgets()

        // Load existing data from block entity
        if (configuration.urls.isEmpty()) {
            configuration.urls.addAll(be.audioUrls)
            configuration.weights.addAll(be.urlWeights)
            configuration.randomMode = be.randomMode
            configuration.currentUrlIndex = be.currentUrlIndex

            // Ensure weights list is properly sized
            while (configuration.weights.size < configuration.urls.size) {
                configuration.weights.add(1f)
            }
        }

        // Initialize buttons
        initializeButtons()

        // Initialize URL input fields
        rebuildUrlInputFields()
    }

    private fun initializeButtons() {
        confirmButton =
            IconButton(guiLeft + background.width - 33, guiTop + background.height - 24, AllIcons.I_CONFIRM).apply {
                withCallback<IconButton> { minecraft?.player?.closeContainer() }
                addRenderableWidget(this)
            }

        modeButton =
            IconButton(
                guiLeft + 10,
                guiTop + background.height - 24,
                if (configuration.randomMode) randomModeTexture else sequentialModeTexture,
            ).apply {
                withCallback<IconButton> {
                    configuration.randomMode = !configuration.randomMode
                    setIcon(if (configuration.randomMode) randomModeTexture else sequentialModeTexture)
                    updateModeButtonTooltip()
                    rebuildUrlInputFields() // Rebuild to adjust card widths
                }
                addRenderableWidget(this)
            }
        updateModeButtonTooltip()

        insertButton =
            AdvancedIconButton(0, 0, addUrlIcon).apply {
                withCallback<IconButton> {
                    configuration.urls.add("")
                    configuration.weights.add(1f)
                    // Don't change currentUrlIndex, keep it pointing to the same entry
                    rebuildUrlInputFields()
                }
                setToolTip(ModLang.translate("gui.record_press_base.url_add").component())
                // render in stencil
            }

        increaseIndexButton =
            IconButton(guiLeft + 50, guiTop + background.height - 24, AllIcons.I_PRIORITY_LOW).apply {
                withCallback<IconButton> {
                    if (configuration.urls.isNotEmpty()) {
                        configuration.currentUrlIndex = (configuration.currentUrlIndex + 1) % configuration.urls.size
                    }
                }
                setToolTip(ModLang.translate("gui.record_press_base.url_index_increase").component())
                addRenderableWidget(this)
            }

        decreaseIndexButton =
            IconButton(guiLeft + 70, guiTop + background.height - 24, AllIcons.I_PRIORITY_HIGH).apply {
                withCallback<IconButton> {
                    if (configuration.urls.isNotEmpty()) {
                        configuration.currentUrlIndex =
                            (configuration.currentUrlIndex - 1 + configuration.urls.size) % configuration.urls.size
                    }
                }
                setToolTip(ModLang.translate("gui.record_press_base.url_index_decrease").component())
                addRenderableWidget(this)
            }
    }

    private fun updateModeButtonTooltip() {
        modeButton.toolTip.clear()
        modeButton.toolTip.add(ModLang.translate("gui.record_press_base.url_select_mode").component())
    }

    private fun rebuildUrlInputFields() {
        // Clear old input fields (don't need to remove from widgets since they're not added)
        urlInputFields.clear()
        weightInputFields.clear()

        // Ensure weights list is properly sized
        while (configuration.weights.size < configuration.urls.size) {
            configuration.weights.add(1f)
        }
        while (configuration.weights.size > configuration.urls.size) {
            configuration.weights.removeAt(configuration.weights.size - 1)
        }

        // Create new input fields for each URL
        configuration.urls.forEachIndexed { index, url ->
            val editBox =
                EditBox(
                    font,
                    0,
                    0, // Position will be set during render
                    getUrlInputWidth(),
                    URL_FIELD_HEIGHT,
                    ModLang.translate("gui.record_press_base.url_input").component(),
                ).apply {
                    value = url
                    setBordered(false)
                    setResponder { newValue ->
                        configuration.urls[index] = newValue
                    }
                }
            urlInputFields.add(editBox)

            // Create weight input field for random mode
            val weightEditBox =
                EditBox(
                    font,
                    0,
                    0, // Position will be set during render
                    30, // Small width for weight input
                    URL_FIELD_HEIGHT,
                    ModLang.translate("gui.record_press_base.weight_input").component(),
                ).apply {
                    value = String.format("%.2f", configuration.weights.getOrElse(index) { 1f })
                    setBordered(false)
                    setResponder { newValue ->
                        if (newValue.isEmpty()) {
                            return@setResponder
                        }
                        try {
                            var weight = newValue.toFloatOrNull()
                            if (weight != null) {
                                // Clamp between 0 and 1
                                weight = weight.coerceIn(0f, 1f)
                                configuration.weights[index] = weight
                                // Update display to show clamped value
                                if (newValue.toFloatOrNull() != weight) {
                                    value = String.format("%.2f", weight)
                                }
                            }
                        } catch (e: Exception) {
                            // Keep previous value if parsing fails
                        }
                    }
                }
            weightInputFields.add(weightEditBox)
            // Don't add as renderable widget - we'll handle rendering and interaction manually
        }
    }

    private fun getLinkIconForUrl(url: String): ModGuiTexture =
        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            youtubeIcon
        } else {
            anyUrlIcon
        }

    override fun renderWindow(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTicks: Float,
    ) {
        val x = guiLeft
        val y = guiTop

        // Render background
        background.render(graphics, x, y)
        graphics.drawString(
            font,
            title,
            x + background.width / 2 - font.width(title) / 2,
            y + 4,
            0x592424,
            false,
        )

        // Setup framebuffer for stencil rendering
        val mcRenderTarget = minecraft?.mainRenderTarget ?: return
        val rendererFrameBuffer = UIRenderHelper.framebuffer ?: return
        UIRenderHelper.swapAndBlitColor(mcRenderTarget, rendererFrameBuffer)

        // Draw background strip (vertical strip at x+33)
        UIRenderHelper.drawStretched(
            graphics,
            x + 33,
            y + SCROLL_AREA_Y,
            3,
            SCROLL_AREA_HEIGHT,
            200,
            AllGuiTextures.SCHEDULE_STRIP_DARK,
        )

        // Render scrollable content
        renderScrollableContent(graphics, mouseX, mouseY, partialTicks)

        // Render gradients for scroll indication
        renderScrollGradients(graphics)

        UIRenderHelper.swapAndBlitColor(rendererFrameBuffer, mcRenderTarget)

        // Render 3D item
        GuiGameElement
            .of(renderedItem)
            .at<GuiGameElement.GuiRenderBuilder>(x + background.width + 6f, y + background.height - 56f, -200f)
            .scale(5.0)
            .render(graphics)
    }

    private fun renderScrollableContent(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTicks: Float,
    ) {
        val x = guiLeft
        val y = guiTop
        val matrixStack = graphics.pose()
        val scrollOffset = -scroll.getValue(partialTicks)
        val entries = configuration.urls

        var yOffset = 25
        totalContentHeight = yOffset
        editBoxPositions.clear()
        cardButtonPositions.clear()

        for (i in 0..entries.size) {
            // Start stencil for clipping
            startStencil(
                graphics,
                (x + SCROLL_AREA_X).toFloat(),
                (y + SCROLL_AREA_Y).toFloat(),
                SCROLL_AREA_WIDTH.toFloat(),
                SCROLL_AREA_HEIGHT.toFloat(),
            )

            matrixStack.pushPose()
            matrixStack.translate(0f, scrollOffset, 0f)

            // Draw top strip for first item
            if (i == 0 || entries.isEmpty()) {
                UIRenderHelper.drawStretched(
                    graphics,
                    x + 33,
                    y + 16,
                    3,
                    10,
                    -100,
                    AllGuiTextures.SCHEDULE_STRIP_LIGHT,
                )
            }

            // Render "add URL" button at the end
            if (i == entries.size) {
                if (i > 0) yOffset += 9
                AllGuiTextures.SCHEDULE_STRIP_END.render(graphics, x + 29, y + yOffset)

                // Render the insert button manually inside the transformed space
                val buttonX = x + 49
                val buttonY = y + yOffset
                insertButton.x = buttonX
                insertButton.y = buttonY
                insertButton.render(graphics, mouseX, mouseY, partialTicks)

                // Track button position for interaction
                insertButtonPosition =
                    WidgetPosition(
                        buttonX,
                        buttonY,
                        insertButton.width,
                        insertButton.height,
                        (buttonY + scrollOffset).toInt(),
                    )

                totalContentHeight = yOffset + 20
                matrixStack.popPose()
                endStencil()
                break
            }

            // Render URL entry card (including EditBox)
            val urlEntry = entries[i]
            val cardHeight = renderUrlEntry(graphics, i, urlEntry, yOffset, mouseX, mouseY, partialTicks, scrollOffset)
            yOffset += cardHeight

            // Draw separator between entries
            if (i + 1 < entries.size) {
                AllGuiTextures.SCHEDULE_STRIP_DOTTED.render(graphics, x + 29, y + yOffset - 3)
                yOffset += CARD_SPACING
            }

            matrixStack.popPose()
            endStencil()
        }

        // Render pointer for current selection AFTER all content (on top layer)
        if (configuration.currentUrlIndex < entries.size && entries.isNotEmpty()) {
            // Calculate yOffset for the selected entry
            var pointerYOffset = 27
            for (i in 0 until configuration.currentUrlIndex) {
                pointerYOffset += CARD_HEADER_HEIGHT + CARD_PADDING + CARD_SPACING
            }
            renderSelectionPointer(graphics, scrollOffset, pointerYOffset)
        }
    }

    private fun renderSelectionPointer(
        graphics: GuiGraphics,
        scrollOffset: Float,
        yOffset: Int,
    ) {
        val x = guiLeft
        val y = guiTop
        val matrixStack = graphics.pose()

        matrixStack.pushPose()
        val expectedY = scrollOffset + y + yOffset + 4
        val actualY =
            Mth.clamp(
                expectedY,
                (y + SCROLL_AREA_Y).toFloat(),
                (y + SCROLL_AREA_Y + SCROLL_AREA_HEIGHT - 15).toFloat(),
            )
        matrixStack.translate(0f, actualY, 0f)
        (if (expectedY == actualY) pointerTexture else pointerOffscreenTexture)
            .render(graphics, x - 14, 0)
        matrixStack.popPose()
    }

    private fun renderScrollGradients(graphics: GuiGraphics) {
        val x = guiLeft
        val y = guiTop
        val zLevel = 200

        // Top gradient
        graphics.fillGradient(
            x + SCROLL_AREA_X,
            y + SCROLL_AREA_Y,
            x + SCROLL_AREA_X + SCROLL_AREA_WIDTH,
            y + SCROLL_AREA_Y + 10,
            zLevel,
            0x77000000,
            0x00000000,
        )

        // Bottom gradient
        graphics.fillGradient(
            x + SCROLL_AREA_X,
            y + SCROLL_AREA_Y + SCROLL_AREA_HEIGHT - 10,
            x + SCROLL_AREA_X + SCROLL_AREA_WIDTH,
            y + SCROLL_AREA_Y + SCROLL_AREA_HEIGHT,
            zLevel,
            0x00000000,
            0x77000000,
        )
    }

    private fun renderUrlEntry(
        graphics: GuiGraphics,
        index: Int,
        urlEntry: String,
        yOffset: Int,
        mouseX: Int,
        mouseY: Int,
        partialTicks: Float,
        scrollOffset: Float,
    ): Int {
        val zLevel = -100
        val cardWidth = CARD_WIDTH
        val cardHeader = CARD_HEADER_HEIGHT
        val cardHeight = cardHeader + CARD_PADDING
        val urlInputWidth = getUrlInputWidth()

        val matrixStack = graphics.pose()
        val cardX = guiLeft + 25
        val cardY = guiTop + yOffset

        matrixStack.pushPose()
        matrixStack.translate(cardX.toFloat(), cardY.toFloat(), 0f)

        // Draw card background
        renderCardBackground(graphics, cardWidth, cardHeight, cardHeader, zLevel)

        // Draw card action buttons and track positions
        renderCardButtons(graphics, index, cardX, cardY, cardWidth, cardHeight, cardHeader, scrollOffset)

        // Draw left strip
        UIRenderHelper.drawStretched(graphics, 8, 0, 3, cardHeight + 10, zLevel, AllGuiTextures.SCHEDULE_STRIP_LIGHT)
        noteStripTexture.render(graphics, 5, CARD_SPACING)

        matrixStack.popPose()

        val middle = AllGuiTextures.SCHEDULE_CONDITION_MIDDLE
        val right = AllGuiTextures.SCHEDULE_CONDITION_RIGHT

        // Render input background
        val inputX = cardX + 26
        val inputY = cardY - 2 + CARD_SPACING

        UIRenderHelper.drawStretched(
            graphics,
            inputX,
            inputY,
            urlInputWidth,
            URL_FIELD_HEIGHT,
            0,
            middle,
        )
        linkArrowTextureLeft.render(graphics, cardX + 16, inputY)
        right.render(graphics, cardX + urlInputWidth + 26, inputY)

        // Render EditBox inline (inside the same transform)
        if (index < urlInputFields.size) {
            val editBox = urlInputFields[index]

            // Set EditBox position (within transformed space)
            editBox.x = inputX
            editBox.y = inputY + 4
            editBox.setWidth(urlInputWidth)
            editBox.height = URL_FIELD_HEIGHT

            // Render the EditBox now
            editBox.render(graphics, mouseX, mouseY, partialTicks)

            // Track position for interaction (with scroll applied)
            val scrolledY = (inputY + scrollOffset).toInt()
            editBoxPositions[index] =
                WidgetPosition(
                    inputX,
                    inputY,
                    urlInputWidth,
                    URL_FIELD_HEIGHT,
                    scrolledY,
                )

            // Render weight input field if in random mode
            if (configuration.randomMode && index < weightInputFields.size) {
                val weightEditBox = weightInputFields[index]
                val weightInputX = inputX + urlInputWidth + 18

                // Draw weight input background
                UIRenderHelper.drawStretched(
                    graphics,
                    weightInputX,
                    inputY,
                    35,
                    URL_FIELD_HEIGHT,
                    0,
                    middle,
                )
                percentageLabelTextureLeft.render(graphics, weightInputX - 8, inputY)
                right.render(graphics, weightInputX + 35, inputY)

                // Set weight EditBox position
                weightEditBox.x = weightInputX + 2
                weightEditBox.y = inputY + 4
                weightEditBox.setWidth(30)
                weightEditBox.height = URL_FIELD_HEIGHT

                // Render the weight EditBox
                weightEditBox.render(graphics, mouseX, mouseY, partialTicks)
            }
        }

        return cardHeight
    }

    private fun renderCardBackground(
        graphics: GuiGraphics,
        cardWidth: Int,
        cardHeight: Int,
        cardHeader: Int,
        zLevel: Int,
    ) {
        val light = AllGuiTextures.SCHEDULE_CARD_LIGHT
        val medium = AllGuiTextures.SCHEDULE_CARD_MEDIUM
        val dark = AllGuiTextures.SCHEDULE_CARD_DARK

        UIRenderHelper.drawStretched(graphics, 0, 1, cardWidth, cardHeight - 2, zLevel, light)
        UIRenderHelper.drawStretched(graphics, 1, 0, cardWidth - 2, cardHeight, zLevel, light)
        UIRenderHelper.drawStretched(graphics, 1, 1, cardWidth - 2, cardHeight - 2, zLevel, dark)
        UIRenderHelper.drawStretched(graphics, 2, 2, cardWidth - 4, cardHeight - 4, zLevel, medium)
        UIRenderHelper.drawStretched(graphics, 2, 2, cardWidth - 4, cardHeader, zLevel, medium)
    }

    private fun renderCardButtons(
        graphics: GuiGraphics,
        index: Int,
        cardX: Int,
        cardY: Int,
        cardWidth: Int,
        cardHeight: Int,
        cardHeader: Int,
        scrollOffset: Float,
    ) {
        // Button dimensions (from AllGuiTextures)
        val buttonSize = 16

        // Remove button (top-right of card)
        val removeX = cardX + cardWidth - 14
        val removeY = cardY + 2
        AllGuiTextures.SCHEDULE_CARD_REMOVE.render(graphics, cardWidth - 14, 2)
        val removeButton =
            WidgetPosition(
                removeX,
                removeY,
                buttonSize,
                buttonSize,
                (removeY + scrollOffset).toInt(),
            )

        // Move up button (right side, above header)
        val moveUpButton =
            if (index > 0) {
                val upX = cardX + cardWidth
                val upY = cardY + cardHeader - 14
                AllGuiTextures.SCHEDULE_CARD_MOVE_UP.render(graphics, cardWidth, cardHeader - 14)
                WidgetPosition(
                    upX,
                    upY,
                    buttonSize,
                    buttonSize,
                    (upY + scrollOffset).toInt(),
                )
            } else {
                null
            }

        // Move down button (right side, below header)
        val moveDownButton =
            if (index < configuration.urls.size - 1) {
                val downX = cardX + cardWidth
                val downY = cardY + cardHeader
                AllGuiTextures.SCHEDULE_CARD_MOVE_DOWN.render(graphics, cardWidth, cardHeader)
                WidgetPosition(
                    downX,
                    downY,
                    buttonSize,
                    buttonSize,
                    (downY + scrollOffset).toInt(),
                )
            } else {
                null
            }

        // Track button positions
        cardButtonPositions[index] =
            CardButtonPositions(
                removeButton = removeButton,
                moveUpButton = moveUpButton,
                moveDownButton = moveDownButton,
            )
    }

    override fun tick() {
        scroll.tickChaser()
        urlInputFields.forEach { it.tick() }
        weightInputFields.forEach { it.tick() }

        // Sync currentUrlIndex from block entity to GUI (for real-time updates during processing)
        if (be.currentUrlIndex != configuration.currentUrlIndex) {
            configuration.currentUrlIndex = be.currentUrlIndex
        }

        super.tick()
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        delta: Double,
    ): Boolean {
        // Calculate maximum scroll based on content height vs visible area
        val maxScroll = max(0, totalContentHeight - SCROLL_AREA_HEIGHT)

        if (maxScroll > 0) {
            var chaseTarget = scroll.getChaseTarget()
            chaseTarget -= (delta * 12).toFloat()
            chaseTarget = Mth.clamp(chaseTarget, 0f, maxScroll.toFloat())
            scroll.chase(chaseTarget.toDouble(), 0.7, Chaser.EXP)
        } else {
            scroll.chase(0.0, 0.7, Chaser.EXP)
        }

        return super.mouseScrolled(mouseX, mouseY, delta)
    }

    override fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        button: Int,
    ): Boolean {
        // Check if click is within scroll area
        val scrollAreaLeft = guiLeft + SCROLL_AREA_X
        val scrollAreaTop = guiTop + SCROLL_AREA_Y
        val scrollAreaRight = scrollAreaLeft + SCROLL_AREA_WIDTH
        val scrollAreaBottom = scrollAreaTop + SCROLL_AREA_HEIGHT

        if (mouseX >= scrollAreaLeft && mouseX <= scrollAreaRight &&
            mouseY >= scrollAreaTop && mouseY <= scrollAreaBottom
        ) {
            // Check if insertButton was clicked (with scroll adjustment)
            insertButtonPosition?.let { pos ->
                if (mouseX >= pos.x && mouseX <= pos.x + pos.width &&
                    mouseY >= pos.scrolledY && mouseY <= pos.scrolledY + pos.height
                ) {
                    insertButton.onClick(mouseX, mouseY)
                    return true
                }
            }

            // Check if any card button was clicked
            cardButtonPositions.forEach { (index, buttons) ->
                // Check remove button
                buttons.removeButton?.let { pos ->
                    if (mouseX >= pos.x && mouseX <= pos.x + pos.width &&
                        mouseY >= pos.scrolledY && mouseY <= pos.scrolledY + pos.height
                    ) {
                        removeUrlEntry(index)
                        return true
                    }
                }

                // Check move up button
                buttons.moveUpButton?.let { pos ->
                    if (mouseX >= pos.x && mouseX <= pos.x + pos.width &&
                        mouseY >= pos.scrolledY && mouseY <= pos.scrolledY + pos.height
                    ) {
                        moveUrlEntryUp(index)
                        return true
                    }
                }

                // Check move down button
                buttons.moveDownButton?.let { pos ->
                    if (mouseX >= pos.x && mouseX <= pos.x + pos.width &&
                        mouseY >= pos.scrolledY && mouseY <= pos.scrolledY + pos.height
                    ) {
                        moveUrlEntryDown(index)
                        return true
                    }
                }
            }

            // Check if any EditBox was clicked
            var clickedEditBox = false
            editBoxPositions.forEach { (index, pos) ->
                if (index < urlInputFields.size) {
                    val editBox = urlInputFields[index]
                    val wasClicked =
                        mouseX >= pos.x && mouseX <= pos.x + pos.width &&
                            mouseY >= pos.scrolledY && mouseY <= pos.scrolledY + pos.height

                    if (wasClicked) {
                        // Unfocus all other EditBoxes first
                        urlInputFields.forEach { it.isFocused = false }
                        weightInputFields.forEach { it.isFocused = false }
                        // Focus and click this EditBox
                        editBox.isFocused = true
                        editBox.onClick(mouseX, mouseY)
                        clickedEditBox = true
                    }
                }
            }

            // Check if any weight EditBox was clicked (only in random mode)
            if (configuration.randomMode && !clickedEditBox) {
                val urlInputWidth = getUrlInputWidth()
                editBoxPositions.forEach { (index, pos) ->
                    if (index < weightInputFields.size) {
                        val weightEditBox = weightInputFields[index]
                        val weightInputX = pos.x + urlInputWidth + 18
                        val wasWeightClicked =
                            mouseX >= weightInputX && mouseX <= weightInputX + 35 &&
                                mouseY >= pos.scrolledY && mouseY <= pos.scrolledY + pos.height

                        if (wasWeightClicked) {
                            // Unfocus all other EditBoxes first
                            urlInputFields.forEach { it.isFocused = false }
                            weightInputFields.forEach { it.isFocused = false }
                            // Focus and click this weight EditBox
                            weightEditBox.isFocused = true
                            weightEditBox.onClick(mouseX, mouseY)
                            clickedEditBox = true
                        }
                    }
                }
            }

            if (clickedEditBox) {
                return true
            }
        } else {
            // Click outside scroll area - unfocus all EditBoxes
            urlInputFields.forEach { it.isFocused = false }
            weightInputFields.forEach { it.isFocused = false }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(
        keyCode: Int,
        scanCode: Int,
        modifiers: Int,
    ): Boolean {
        // Check if any EditBox is focused
        val hasEditBoxFocused = urlInputFields.any { it.isFocused } || weightInputFields.any { it.isFocused }

        // If an EditBox is focused, prevent inventory key from closing the screen
        if (hasEditBoxFocused) {
            val inventoryKey = minecraft?.options?.keyInventory
            if (inventoryKey != null && inventoryKey.matches(keyCode, scanCode)) {
                // Don't close the screen, let the EditBox handle the key
                return false
            }
        }

        // Forward key events to focused EditBox
        urlInputFields.forEach { editBox ->
            if (editBox.isFocused && editBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true
            }
        }
        weightInputFields.forEach { editBox ->
            if (editBox.isFocused && editBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(
        codePoint: Char,
        modifiers: Int,
    ): Boolean {
        // Forward character input to focused EditBox
        urlInputFields.forEach { editBox ->
            if (editBox.isFocused && editBox.charTyped(codePoint, modifiers)) {
                return true
            }
        }
        weightInputFields.forEach { editBox ->
            if (editBox.isFocused && editBox.charTyped(codePoint, modifiers)) {
                return true
            }
        }
        return super.charTyped(codePoint, modifiers)
    }

    override fun mouseDragged(
        mouseX: Double,
        mouseY: Double,
        button: Int,
        dragX: Double,
        dragY: Double,
    ): Boolean {
        // Forward drag events to focused EditBox for text selection
        urlInputFields.forEach { editBox ->
            if (editBox.isFocused) {
                return editBox.mouseDragged(mouseX, mouseY, button, dragX, dragY)
            }
        }
        weightInputFields.forEach { editBox ->
            if (editBox.isFocused) {
                return editBox.mouseDragged(mouseX, mouseY, button, dragX, dragY)
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun removed() {
        sendPacket()
    }

    private fun removeUrlEntry(index: Int) {
        if (index >= 0 && index < configuration.urls.size) {
            configuration.urls.removeAt(index)
            if (index < configuration.weights.size) {
                configuration.weights.removeAt(index)
            }

            // Adjust currentUrlIndex if needed
            if (configuration.currentUrlIndex >= configuration.urls.size && configuration.urls.isNotEmpty()) {
                configuration.currentUrlIndex = configuration.urls.size - 1
            } else if (configuration.urls.isEmpty()) {
                configuration.currentUrlIndex = 0
            } else if (configuration.currentUrlIndex > index) {
                configuration.currentUrlIndex--
            }

            rebuildUrlInputFields()
        }
    }

    private fun moveUrlEntryUp(index: Int) {
        if (index > 0 && index < configuration.urls.size) {
            // Swap with previous entry
            val temp = configuration.urls[index]
            configuration.urls[index] = configuration.urls[index - 1]
            configuration.urls[index - 1] = temp

            // Swap weights too
            if (index < configuration.weights.size && index - 1 < configuration.weights.size) {
                val tempWeight = configuration.weights[index]
                configuration.weights[index] = configuration.weights[index - 1]
                configuration.weights[index - 1] = tempWeight
            }

            // Update currentUrlIndex if it was pointing to one of the swapped entries
            when (configuration.currentUrlIndex) {
                index -> configuration.currentUrlIndex = index - 1
                index - 1 -> configuration.currentUrlIndex = index
            }

            rebuildUrlInputFields()
        }
    }

    private fun moveUrlEntryDown(index: Int) {
        if (index >= 0 && index < configuration.urls.size - 1) {
            // Swap with next entry
            val temp = configuration.urls[index]
            configuration.urls[index] = configuration.urls[index + 1]
            configuration.urls[index + 1] = temp

            // Swap weights too
            if (index < configuration.weights.size && index + 1 < configuration.weights.size) {
                val tempWeight = configuration.weights[index]
                configuration.weights[index] = configuration.weights[index + 1]
                configuration.weights[index + 1] = tempWeight
            }

            // Update currentUrlIndex if it was pointing to one of the swapped entries
            when (configuration.currentUrlIndex) {
                index -> configuration.currentUrlIndex = index + 1
                index + 1 -> configuration.currentUrlIndex = index
            }

            rebuildUrlInputFields()
        }
    }

    private fun sendPacket() {
        ModPackets.channel.sendToServer(
            ConfigureRecordPressBasePacket(
                be.blockPos,
                configuration.urls,
                configuration.weights,
                configuration.randomMode,
                configuration.currentUrlIndex,
            ),
        )
    }
}
