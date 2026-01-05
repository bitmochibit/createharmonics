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

        private const val CARD_WIDTH = 160
        private const val CARD_HEADER_HEIGHT = 30
        private const val CARD_PADDING = 4
        private const val CARD_SPACING = 10

        private const val URL_FIELD_WIDTH = 90
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

    // UI Components
    private lateinit var confirmButton: IconButton
    private lateinit var insertButton: IconButton
    private lateinit var modeButton: IconButton
    private lateinit var increaseIndexButton: IconButton
    private lateinit var decreaseIndexButton: IconButton
    private val urlInputFields = mutableListOf<EditBox>()

    // State
    private val scroll = LerpedFloat.linear().startWithValue(0.0)
    private val renderedItem = ItemStack(ModBlocks.RECORD_PRESS_BASE.get())

    data class Configuration(
        val urls: MutableList<String> = mutableListOf(),
        var sequentialMode: Boolean = true,
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

    override fun init() {
        setWindowSize(background.width, background.height)
        super.init()
        clearWidgets()

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
                if (configuration.sequentialMode) sequentialModeTexture else randomModeTexture,
            ).apply {
                withCallback<IconButton> {
                    configuration.sequentialMode = !configuration.sequentialMode
                    setIcon(if (configuration.sequentialMode) sequentialModeTexture else randomModeTexture)
                    updateModeButtonTooltip()
                }
                addRenderableWidget(this)
            }
        updateModeButtonTooltip()

        insertButton =
            AdvancedIconButton(0, 0, addUrlIcon).apply {
                withCallback<IconButton> {
                    configuration.urls.add("")
                    configuration.currentUrlIndex = configuration.urls.size - 1
                    rebuildUrlInputFields()
                }
                setToolTip(ModLang.translate("gui.record_press_base.url_add").component())
                // Don't add as renderable widget - it will be manually rendered inside stencil
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

        // Create new input fields for each URL
        configuration.urls.forEachIndexed { index, url ->
            val editBox =
                EditBox(
                    font,
                    0,
                    0, // Position will be set during render
                    URL_FIELD_WIDTH,
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

        val left = AllGuiTextures.SCHEDULE_CONDITION_LEFT
        val middle = AllGuiTextures.SCHEDULE_CONDITION_MIDDLE
        val right = AllGuiTextures.SCHEDULE_CONDITION_RIGHT

        // Render input background
        val inputX = cardX + 26
        val inputY = cardY - 2 + CARD_SPACING

        UIRenderHelper.drawStretched(
            graphics,
            inputX,
            inputY,
            URL_FIELD_WIDTH,
            URL_FIELD_HEIGHT,
            0,
            middle,
        )
        left.render(graphics, cardX + 20, inputY)
        right.render(graphics, cardX + URL_FIELD_WIDTH + 26, inputY)

        // Render EditBox inline (inside the same transform)
        if (index < urlInputFields.size) {
            val editBox = urlInputFields[index]

            // Set EditBox position (within transformed space)
            editBox.x = inputX
            editBox.y = inputY
            editBox.setWidth(URL_FIELD_WIDTH)
            editBox.height = URL_FIELD_HEIGHT

            // Render the EditBox now
            editBox.render(graphics, mouseX, mouseY, partialTicks)

            // Track position for interaction (with scroll applied)
            val scrolledY = (inputY + scrollOffset).toInt()
            editBoxPositions[index] =
                WidgetPosition(
                    inputX,
                    inputY,
                    URL_FIELD_WIDTH,
                    URL_FIELD_HEIGHT,
                    scrolledY,
                )
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

    override fun render(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTicks: Float,
    ) {
        super.render(graphics, mouseX, mouseY, partialTicks)
    }

    override fun tick() {
        scroll.tickChaser()
        urlInputFields.forEach { it.tick() }
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
                        // Focus and click this EditBox
                        editBox.isFocused = true
                        editBox.onClick(mouseX, mouseY)
                        clickedEditBox = true
                    }
                }
            }

            if (clickedEditBox) {
                return true
            }
        } else {
            // Click outside scroll area - unfocus all EditBoxes
            urlInputFields.forEach { it.isFocused = false }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(
        keyCode: Int,
        scanCode: Int,
        modifiers: Int,
    ): Boolean {
        // Forward key events to focused EditBox
        urlInputFields.forEach { editBox ->
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
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun removed() {
        sendPacket()
    }

    private fun removeUrlEntry(index: Int) {
        if (index >= 0 && index < configuration.urls.size) {
            configuration.urls.removeAt(index)

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
                configuration.sequentialMode,
            ),
        )
    }
}
