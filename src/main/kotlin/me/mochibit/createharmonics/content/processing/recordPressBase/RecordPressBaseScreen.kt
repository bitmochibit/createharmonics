package me.mochibit.createharmonics.content.processing.recordPressBase

import com.simibubi.create.foundation.gui.AllIcons
import com.simibubi.create.foundation.gui.widget.IconButton
import me.mochibit.createharmonics.network.packet.ConfigureRecordPressBasePacket
import me.mochibit.createharmonics.registry.ModBlocks
import me.mochibit.createharmonics.registry.ModGuiTextures
import me.mochibit.createharmonics.registry.ModLang
import me.mochibit.createharmonics.registry.ModPackets
import net.createmod.catnip.animation.LerpedFloat
import net.createmod.catnip.gui.AbstractSimiScreen
import net.createmod.catnip.gui.element.GuiGameElement
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import kotlin.math.max

class RecordPressBaseScreen(
    val be: RecordPressBaseBlockEntity,
) : AbstractSimiScreen(ModLang.translate("gui.record_press_base.title").component()) {
    private val background = ModGuiTextures.RECORD_PRESS_BASE

    private lateinit var confirmButton: IconButton
    private val urlInputs = mutableListOf<EditBox>()
    private val removeButtons = mutableListOf<IconButton>()
    private lateinit var addUrlButton: IconButton
    private lateinit var modeButton: IconButton

    private val scroll = LerpedFloat.linear().startWithValue(0.0)
    private var randomMode: Boolean = false

    // Local copy of URLs to avoid null reference issues
    private val localUrls = mutableListOf<String>()

    private var lastModification = -1

    private val renderedItem = ItemStack(ModBlocks.RECORD_PRESS_BASE.get())

    // Scrolling constants
    private val inputHeight = 20
    private val scrollAreaTop = 27
    private val scrollAreaHeight = 85
    private val inputsPerPage = 4 // How many inputs are visible at once

    override fun init() {
        setWindowSize(background.width, background.height)
        setWindowOffset(-20, 0)
        super.init()

        val x = guiLeft
        val y = guiTop

        // Initialize local URLs with a copy from the block entity
        randomMode = be.randomMode
        localUrls.clear()
        localUrls.addAll(be.audioUrls)
        if (localUrls.isEmpty()) {
            localUrls.add("") // Start with at least one empty URL field
        }

        // Create URL input fields - create ALL of them
        createAllUrlInputs()

        // Add URL button
        addUrlButton = IconButton(x + 155, y + background.height - 48, AllIcons.I_ADD)
        addUrlButton.withCallback<IconButton> {
            localUrls.add("")
            // Scroll to bottom to show new URL
            val maxScrollValue = getMaxScrollValue()
            scroll.chase(maxScrollValue.toFloat().toDouble(), 0.7, LerpedFloat.Chaser.EXP)
            createAllUrlInputs()
            lastModification = 0
        }
        addRenderableWidget(addUrlButton)

        // Mode selection button (Random/Ordered)
        modeButton =
            IconButton(
                x + 21,
                y + background.height - 48,
                if (randomMode) AllIcons.I_TRASH else AllIcons.I_PRIORITY_LOW,
            )
        modeButton.withCallback<IconButton> {
            randomMode = !randomMode
            modeButton.setIcon(if (randomMode) AllIcons.I_TRASH else AllIcons.I_PRIORITY_LOW)
            modeButton.setToolTip(
                Component.translatable(
                    if (randomMode) "gui.record_press_base.mode.random" else "gui.record_press_base.mode.ordered",
                ),
            )
            lastModification = 0
        }
        modeButton.setToolTip(
            Component.translatable(
                if (randomMode) "gui.record_press_base.mode.random" else "gui.record_press_base. mode.ordered",
            ),
        )
        addRenderableWidget(modeButton)

        // Confirm button
        confirmButton = IconButton(x + background.width - 33, y + background.height - 24, AllIcons.I_CONFIRM)
        confirmButton.withCallback<IconButton> { onClose() }
        addRenderableWidget(confirmButton)
    }

    private fun createAllUrlInputs() {
        // Remove old widgets
        urlInputs.forEach { removeWidget(it) }
        removeButtons.forEach { removeWidget(it) }
        urlInputs.clear()
        removeButtons.clear()

        val x = guiLeft
        val y = guiTop

        // Create ALL input fields (not just visible ones)
        for (i in localUrls.indices) {
            val yPos = y + scrollAreaTop + 3 + i * inputHeight

            // URL input field
            val urlInput =
                EditBox(
                    font,
                    x + 45,
                    yPos,
                    90,
                    12,
                    Component.translatable("gui.record_press_base.url_input"),
                )
            urlInput.setBordered(false)
            urlInput.setMaxLength(500)
            urlInput.value = localUrls[i]
            val capturedIndex = i
            urlInput.setResponder {
                if (capturedIndex < localUrls.size) {
                    localUrls[capturedIndex] = it
                    lastModification = 0
                }
            }
            addRenderableWidget(urlInput)
            urlInputs.add(urlInput)

            // Remove button (only if more than one URL)
            if (localUrls.size > 1) {
                val removeButton = IconButton(x + 138, yPos - 2, AllIcons.I_TRASH)
                val capturedIndexForRemove = i
                removeButton.withCallback<IconButton> {
                    if (capturedIndexForRemove < localUrls.size) {
                        localUrls.removeAt(capturedIndexForRemove)
                        createAllUrlInputs()
                        // Adjust scroll if needed
                        val maxScrollValue = getMaxScrollValue()
                        if (scroll.getValue() > maxScrollValue) {
                            scroll.updateChaseTarget(maxScrollValue.toFloat())
                        }
                        lastModification = 0
                    }
                }
                addRenderableWidget(removeButton)
                removeButtons.add(removeButton)
            }
        }
    }

    private fun getMaxScrollValue(): Int = max(0, (localUrls.size - inputsPerPage) * inputHeight)

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
        val scrollValue = scroll.getValue(partialTicks)

        // Enable scissor to clip the scrolling area
        graphics.enableScissor(
            x + 21,
            y + scrollAreaTop,
            x + 155,
            y + scrollAreaTop + scrollAreaHeight,
        )

        matrixStack.pushPose()
        matrixStack.translate(0f, -scrollValue, 0f)

        // Render URL input section background
        for (i in localUrls.indices) {
            val yPos = y + scrollAreaTop + i * inputHeight
            ModGuiTextures.RECORD_PRESS_BASE_INPUT.render(graphics, x + 42, yPos)
            ModGuiTextures.RECORD_PRESS_BASE_LINK_ICON.render(graphics, x + 21, yPos)
        }

        matrixStack.popPose()
        graphics.disableScissor()

        // Render mode label
        val modeText =
            Component.translatable(
                if (randomMode) "gui.record_press_base.mode.random" else "gui.record_press_base.mode.ordered",
            )
        graphics.drawString(
            font,
            modeText,
            x + 45,
            y + background.height - 44,
            0x592424,
            false,
        )

        GuiGameElement
            .of(renderedItem)
            .at<GuiGameElement.GuiRenderBuilder>(x + background.width + 6f, y + background.height - 56f, -200f)
            .scale(5.0)
            .render(graphics)
    }

    override fun render(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTicks: Float,
    ) {
        super.render(graphics, mouseX, mouseY, partialTicks)

        // Apply scroll offset to widgets
        val scrollValue = scroll.getValue(partialTicks).toInt()
        for (i in urlInputs.indices) {
            val yOffset = -scrollValue
            urlInputs[i].setY(guiTop + scrollAreaTop + 3 + i * inputHeight + yOffset)
            if (i < removeButtons.size) {
                removeButtons[i].setY(guiTop + scrollAreaTop + 1 + i * inputHeight + yOffset)
            }
        }
    }

    override fun tick() {
        super.tick()

        if (lastModification >= 0) {
            lastModification++
        }

        if (lastModification >= 20) {
            lastModification = -1
            sendPacket()
        }

        urlInputs.forEach { it.tick() }
        scroll.tickChaser()
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        delta: Double,
    ): Boolean {
        if (super.mouseScrolled(mouseX, mouseY, delta)) {
            return true
        }

        val x = guiLeft
        val y = guiTop
        val scrollAreaLeft = x + 21
        val scrollAreaRight = x + 155
        val scrollAreaTopPos = y + scrollAreaTop
        val scrollAreaBottom = y + scrollAreaTop + scrollAreaHeight

        // Check if mouse is in the scroll area
        if (mouseX >= scrollAreaLeft && mouseX <= scrollAreaRight &&
            mouseY >= scrollAreaTopPos && mouseY <= scrollAreaBottom
        ) {
            val maxScrollValue = getMaxScrollValue()

            if (maxScrollValue > 0) {
                // Calculate new scroll target
                var chaseTarget = scroll.getChaseTarget() - (delta * 12).toFloat()
                chaseTarget = Mth.clamp(chaseTarget, 0f, maxScrollValue.toFloat())

                // Use chase for smooth scrolling (like ScheduleScreen does)
                scroll.chase(chaseTarget.toDouble(), 0.7, LerpedFloat.Chaser.EXP)
                return true
            }
        }

        return false
    }

    override fun removed() {
        sendPacket()
    }

    private fun sendPacket() {
        ModPackets.channel.sendToServer(
            ConfigureRecordPressBasePacket(
                be.blockPos,
                localUrls.toMutableList(),
                randomMode,
            ),
        )
    }
}
