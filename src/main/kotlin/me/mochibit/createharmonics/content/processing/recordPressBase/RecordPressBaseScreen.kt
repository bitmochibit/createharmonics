package me.mochibit.createharmonics.content.processing.recordPressBase

import com.simibubi.create.content.trains.schedule.ScheduleScreen
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
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import kotlin.math.max
import kotlin.math.min

class RecordPressBaseScreen(
    val be: RecordPressBaseBlockEntity,
) : AbstractSimiScreen(ModLang.translate("gui.record_press_base.title").component()),
    ScreenWithStencils {
    // TEXTURES
    private val background = ModGuiTexture("record_press_base", 0, 0, 234, 176)

    private val pointerTexture = ModGuiTexture("record_press_base", 185, 239, 21, 16)
    private val pointerOffscreenTexture = ModGuiTexture("record_press_base", 171, 244, 13, 6)

    private val addUrlIcon = ModGuiTexture("record_press_base", 79, 239, 16, 16)
    private val youtubeIcon = ModGuiTexture("record_press_base", 79, 239, 16, 16)
    private val anyUrlIcon = ModGuiTexture("record_press_base", 79, 239, 16, 16)

    private lateinit var confirmButton: IconButton
    private lateinit var insertButton: IconButton
    private lateinit var modeButton: IconButton
    private lateinit var increaseIndexButton: IconButton
    private lateinit var decreaseIndexButton: IconButton

    private val scroll = LerpedFloat.linear().startWithValue(0.0)

    private val renderedItem = ItemStack(ModBlocks.RECORD_PRESS_BASE.get())

    data class Configuration(
        val urls: MutableList<String> = mutableListOf(),
        var sequentialMode: Boolean = true,
        var currentUrlIndex: Int = 0,
    )

    val configuration = Configuration()

    override fun init() {
        setWindowSize(background.width, background.height)
        super.init()
        clearWidgets()
        val x = guiLeft
        val y = guiTop

        confirmButton =
            IconButton(guiLeft + background.width - 42, guiTop + background.height - 30, AllIcons.I_CONFIRM).apply {
                withCallback<IconButton> { minecraft?.player?.closeContainer() }
                addRenderableWidget(this)
            }

        modeButton =
            IconButton(guiLeft + 21, guiTop + 196, AllIcons.I_TUNNEL_RANDOMIZE).apply {
                withCallback<IconButton> {
                    configuration.sequentialMode = !configuration.sequentialMode
                }
                toolTip.clear()
                toolTip.add(
                    ModLang.translate("gui.record_press_base.url_select_mode").component(),
                )
                addRenderableWidget(this)
            }

        insertButton =
            AdvancedIconButton(0, 0, addUrlIcon).apply {
                withCallback<IconButton> {
                    configuration.urls.add("")
                    configuration.currentUrlIndex = configuration.urls.size - 1
                }
                setToolTip(ModLang.translate("gui.record_press_base.url_add").component())

                addRenderableWidget(this)
            }

        increaseIndexButton =
            IconButton(guiLeft + 45, guiTop + 196, AllIcons.I_PRIORITY_LOW).apply {
                withCallback<IconButton> {
                    configuration.currentUrlIndex++
                    configuration.currentUrlIndex %= configuration.urls.size
                }
                setToolTip(ModLang.translate("gui.record_press_base.url_index_increase").component())
            }

        decreaseIndexButton =
            IconButton(guiLeft + 73, guiTop + 196, AllIcons.I_PRIORITY_HIGH).apply {
                withCallback<IconButton> {
                    configuration.currentUrlIndex--
                    configuration.currentUrlIndex %= configuration.urls.size
                }
                setToolTip(ModLang.translate("gui.record_press_base.url_index_decrease").component())
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
        background.render(graphics, x, y)
        graphics.drawString(
            font,
            title,
            x + background.width / 2 - font.width(title) / 2,
            y + 4,
            0x592424,
            false,
        )

        val matrixStack = graphics.pose()
        val mcRenderTarget = minecraft?.mainRenderTarget ?: return
        val rendererFrameBuffer = UIRenderHelper.framebuffer ?: return
        UIRenderHelper.swapAndBlitColor(mcRenderTarget, rendererFrameBuffer)

        UIRenderHelper.drawStretched(
            graphics,
            x + 33,
            y + 16,
            3,
            173,
            200,
            AllGuiTextures.SCHEDULE_STRIP_DARK,
        )

        var yOffset = 25
        val entries: MutableList<String> = configuration.urls
        val scrollOffset = -scroll.getValue(partialTicks)

        for (i in 0..entries.size) {
            if (configuration.currentUrlIndex == i && !configuration.urls.isEmpty()) {
                matrixStack.pushPose()
                val expectedY: Float = scrollOffset + y + yOffset + 4
                val actualY = Mth.clamp(expectedY, (y + 18).toFloat(), (y + 170).toFloat())
                matrixStack.translate(0f, actualY, 0f)
                (if (expectedY == actualY) pointerTexture else pointerOffscreenTexture)
                    .render(graphics, x, 0)
                matrixStack.popPose()
            }

            startStencil(graphics, (x + 16).toFloat(), (y + 16).toFloat(), 220f, 173f)
            matrixStack.pushPose()
            matrixStack.translate(0f, scrollOffset, 0f)
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

            if (i == entries.size) {
                if (i > 0) yOffset += 9
                AllGuiTextures.SCHEDULE_STRIP_END.render(graphics, x + 29, y + yOffset)
                insertButton.x = x + 49
                insertButton.y = y + yOffset
                insertButton.render(graphics, mouseX, mouseY, partialTicks)
                matrixStack.popPose()
                endStencil()
                break
            }

            val scheduleEntry = entries.get(i)
            val cardY = yOffset
            val cardHeight: Int = renderUrlEntry(graphics, scheduleEntry, cardY, mouseX, mouseY, partialTicks)
            yOffset += cardHeight

            if (i + 1 < entries.size) {
                AllGuiTextures.SCHEDULE_STRIP_DOTTED.render(graphics, x + 29, y + yOffset - 3)
                yOffset += 10
            }

            matrixStack.popPose()
            endStencil()
        }

        val zLevel = 200
        graphics.fillGradient(
            x + 3,
            y + 16,
            x + 223,
            y + 16 + 10,
            zLevel,
            0x77000000,
            0x00000000,
        )
        graphics.fillGradient(
            x + 3,
            y + 135,
            x + 223,
            y + 135 + 10,
            zLevel,
            0x00000000,
            0x77000000,
        )
        UIRenderHelper.swapAndBlitColor(rendererFrameBuffer, mcRenderTarget)

        GuiGameElement
            .of(renderedItem)
            .at<GuiGameElement.GuiRenderBuilder>(x + background.width + 6f, y + background.height - 56f, -200f)
            .scale(5.0)
            .render(graphics)
    }

    fun renderUrlEntry(
        graphics: GuiGraphics,
        urlEntry: String,
        yOffset: Int,
        mouseX: Int,
        mouseY: Int,
        partialTicks: Float,
    ): Int {
        val zLevel = -100

        val light = AllGuiTextures.SCHEDULE_CARD_LIGHT
        val medium = AllGuiTextures.SCHEDULE_CARD_MEDIUM
        val dark = AllGuiTextures.SCHEDULE_CARD_DARK

        val cardWidth = 120
        val cardHeader = 40
        val cardHeight = cardHeader + 4

        val matrixStack = graphics.pose()
        matrixStack.pushPose()
        matrixStack.translate((guiLeft + 25).toFloat(), (guiTop + yOffset).toFloat(), 0f)

        UIRenderHelper.drawStretched(graphics, 0, 1, cardWidth, cardHeight - 2, zLevel, light)
        UIRenderHelper.drawStretched(graphics, 1, 0, cardWidth - 2, cardHeight, zLevel, light)
        UIRenderHelper.drawStretched(graphics, 1, 1, cardWidth - 2, cardHeight - 2, zLevel, dark)
        UIRenderHelper.drawStretched(graphics, 2, 2, cardWidth - 4, cardHeight - 4, zLevel, medium)
        UIRenderHelper.drawStretched(
            graphics,
            2,
            2,
            cardWidth - 4,
            cardHeader,
            zLevel,
            medium,
        )

        AllGuiTextures.SCHEDULE_CARD_REMOVE.render(graphics, cardWidth - 14, 2)
        AllGuiTextures.SCHEDULE_CARD_DUPLICATE.render(graphics, cardWidth - 14, cardHeight - 14)

        val i: Int = configuration.urls.indexOf(urlEntry)
        if (i > 0) AllGuiTextures.SCHEDULE_CARD_MOVE_UP.render(graphics, cardWidth, cardHeader - 14)
        if (i < configuration.urls.size - 1) {
            AllGuiTextures.SCHEDULE_CARD_MOVE_DOWN.render(
                graphics,
                cardWidth,
                cardHeader,
            )
        }

        UIRenderHelper.drawStretched(graphics, 8, 0, 3, cardHeight + 10, zLevel, AllGuiTextures.SCHEDULE_STRIP_LIGHT)
        AllGuiTextures.SCHEDULE_STRIP_ACTION
            .render(graphics, 4, 6)

        renderInput(graphics, urlEntry, 26, 5, false, 100, mouseX, mouseY, partialTicks)

        matrixStack.popPose()

        return cardHeight
    }

    protected fun renderInput(
        graphics: GuiGraphics,
        url: String,
        x: Int,
        y: Int,
        clean: Boolean,
        minSize: Int,
        mouseX: Int,
        mouseY: Int,
        partialTicks: Float,
    ): Int {
        val fieldSize = min(60, 150)
        val matrixStack = graphics.pose()
        matrixStack.pushPose()

        val left =
            if (clean) AllGuiTextures.SCHEDULE_CONDITION_LEFT_CLEAN else AllGuiTextures.SCHEDULE_CONDITION_LEFT
        val middle = AllGuiTextures.SCHEDULE_CONDITION_MIDDLE
        val right = AllGuiTextures.SCHEDULE_CONDITION_RIGHT

        val urlInputBux =
            EditBox(
                font,
                0,
                0,
                fieldSize,
                16,
                ModLang.translate("gui.record_press_base.url_input").component(),
            )

        // urlInputBux.setBordered(false)

        matrixStack.translate(x.toFloat(), y.toFloat(), 0f)
        UIRenderHelper.drawStretched(graphics, 0, 0, fieldSize, 16, -100, middle)
        left.render(graphics, if (clean) 0 else -3, 0)
        right.render(graphics, fieldSize - 2, 0)
        urlInputBux.render(graphics, mouseX, mouseY, partialTicks)
        addWidget(urlInputBux)

        matrixStack.popPose()
        return fieldSize
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
        super.tick()
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        delta: Double,
    ): Boolean {
        var chaseTarget = scroll.getChaseTarget()
        val max = (40 - 173).toFloat()
        if (max > 0) {
            chaseTarget -= (delta * 12).toFloat()
            chaseTarget = Mth.clamp(chaseTarget, 0f, max)
            scroll.chase(chaseTarget.toInt().toDouble(), 0.7, Chaser.EXP)
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
