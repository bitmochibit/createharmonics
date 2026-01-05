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
        private const val SCROLL_AREA_X = 16
        private const val SCROLL_AREA_Y = 16
        private const val SCROLL_AREA_WIDTH = 220
        private const val SCROLL_AREA_HEIGHT = 173

        private const val CARD_WIDTH = 160
        private const val CARD_HEADER_HEIGHT = 40
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
            IconButton(guiLeft + background.width - 42, guiTop + background.height - 30, AllIcons.I_CONFIRM).apply {
                withCallback<IconButton> { minecraft?.player?.closeContainer() }
                addRenderableWidget(this)
            }

        modeButton =
            IconButton(guiLeft + 21, guiTop + 196, AllIcons.I_TUNNEL_RANDOMIZE).apply {
                withCallback<IconButton> {
                    configuration.sequentialMode = !configuration.sequentialMode
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
                addRenderableWidget(this)
            }

        increaseIndexButton =
            IconButton(guiLeft + 45, guiTop + 196, AllIcons.I_PRIORITY_LOW).apply {
                withCallback<IconButton> {
                    if (configuration.urls.isNotEmpty()) {
                        configuration.currentUrlIndex = (configuration.currentUrlIndex + 1) % configuration.urls.size
                    }
                }
                setToolTip(ModLang.translate("gui.record_press_base.url_index_increase").component())
                addRenderableWidget(this)
            }

        decreaseIndexButton =
            IconButton(guiLeft + 73, guiTop + 196, AllIcons.I_PRIORITY_HIGH).apply {
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
        // Remove old input fields
        urlInputFields.forEach { removeWidget(it) }
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
            addRenderableWidget(editBox)
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

        // Draw background strip
        UIRenderHelper.drawStretched(
            graphics,
            x + 33,
            y + 16,
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

        for (i in 0..entries.size) {
            // Render pointer for current selection
            if (configuration.currentUrlIndex == i && entries.isNotEmpty()) {
                renderSelectionPointer(graphics, scrollOffset, yOffset)
            }

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
                insertButton.x = x + 49
                insertButton.y = y + yOffset
                insertButton.render(graphics, mouseX, mouseY, partialTicks)
                totalContentHeight = yOffset + 20
                matrixStack.popPose()
                endStencil()
                break
            }

            // Render URL entry card
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
        val actualY = Mth.clamp(expectedY, (y + 18).toFloat(), (y + 170).toFloat())
        matrixStack.translate(0f, actualY, 0f)
        (if (expectedY == actualY) pointerTexture else pointerOffscreenTexture)
            .render(graphics, x, 0)
        matrixStack.popPose()
    }

    private fun renderScrollGradients(graphics: GuiGraphics) {
        val x = guiLeft
        val y = guiTop
        val zLevel = 200

        // Top gradient
        graphics.fillGradient(
            x + 3,
            y + SCROLL_AREA_Y,
            x + SCROLL_AREA_X + SCROLL_AREA_WIDTH - 13,
            y + SCROLL_AREA_Y + 10,
            zLevel,
            0x77000000,
            0x00000000,
        )

        // Bottom gradient
        graphics.fillGradient(
            x + 3,
            y + 135,
            x + SCROLL_AREA_X + SCROLL_AREA_WIDTH - 13,
            y + 145,
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

        // Draw card action buttons
        renderCardButtons(graphics, index, cardWidth, cardHeight, cardHeader)

        // Draw left strip
        UIRenderHelper.drawStretched(graphics, 8, 0, 3, cardHeight + 10, zLevel, AllGuiTextures.SCHEDULE_STRIP_LIGHT)
        AllGuiTextures.SCHEDULE_STRIP_ACTION.render(graphics, 4, 6)

        matrixStack.popPose()
        matrixStack.pushPose()

        val left = AllGuiTextures.SCHEDULE_CONDITION_LEFT
        val middle = AllGuiTextures.SCHEDULE_CONDITION_MIDDLE
        val right = AllGuiTextures.SCHEDULE_CONDITION_RIGHT

        // Position and render the URL input field with absolute coordinates
        if (index < urlInputFields.size) {
            val editBox = urlInputFields[index]
            // Calculate absolute position considering scroll offset
            val absoluteY = (cardY + URL_FIELD_HEIGHT + scrollOffset).toInt()
            UIRenderHelper.drawStretched(
                graphics,
                cardX + 26,
                cardY + URL_FIELD_HEIGHT - URL_FIELD_HEIGHT / 4,
                URL_FIELD_WIDTH,
                URL_FIELD_HEIGHT,
                0,
                middle,
            )
            left.render(graphics, cardX + 20, cardY + URL_FIELD_HEIGHT - URL_FIELD_HEIGHT / 4)
            right.render(graphics, cardX + URL_FIELD_WIDTH + 26, cardY + URL_FIELD_HEIGHT - URL_FIELD_HEIGHT / 4)

            editBox.x = cardX + 26
            editBox.y = absoluteY
            editBox.setWidth(URL_FIELD_WIDTH)
            editBox.height = URL_FIELD_HEIGHT
        }

        matrixStack.popPose()

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
        cardWidth: Int,
        cardHeight: Int,
        cardHeader: Int,
    ) {
        // Remove button
        AllGuiTextures.SCHEDULE_CARD_REMOVE.render(graphics, cardWidth - 14, 2)

        // Duplicate button
        AllGuiTextures.SCHEDULE_CARD_DUPLICATE.render(graphics, cardWidth - 14, cardHeight - 14)

        // Move up button
        if (index > 0) {
            AllGuiTextures.SCHEDULE_CARD_MOVE_UP.render(graphics, cardWidth, cardHeader - 14)
        }

        // Move down button
        if (index < configuration.urls.size - 1) {
            AllGuiTextures.SCHEDULE_CARD_MOVE_DOWN.render(graphics, cardWidth, cardHeader)
        }
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

    override fun removed() {
        sendPacket()
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
