package me.mochibit.createharmonics.content.block.recordPressBase

import com.simibubi.create.foundation.gui.AllIcons
import com.simibubi.create.foundation.gui.widget.IconButton
import me.mochibit.createharmonics.network.packet.ConfigureRecordPressBasePacket
import me.mochibit.createharmonics.registry.ModBlocks
import me.mochibit.createharmonics.registry.ModGuiTextures
import me.mochibit.createharmonics.registry.ModLang
import me.mochibit.createharmonics.registry.ModPackets
import net.createmod.catnip.gui.AbstractSimiScreen
import net.createmod.catnip.gui.element.GuiGameElement
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

class RecordPressBaseScreen(
    val be: RecordPressBaseBlockEntity,
) : AbstractSimiScreen(ModLang.translate("gui.record_press_base.title").component()) {
    private val background = ModGuiTextures.RECORD_PRESS_BASE

    private lateinit var confirmButton: IconButton
    private lateinit var urlInput: EditBox
    private lateinit var nameInput: EditBox

    private var lastModification = -1

    private val renderedItem = ItemStack(ModBlocks.RECORD_PRESS_BASE.get())

    override fun init() {
        setWindowSize(background.width, background.height)
        setWindowOffset(-20, 0)
        super.init()

        val x = guiLeft
        val y = guiTop

        // URL input field
        urlInput =
            EditBox(
                font,
                x + 45,
                y + 30,
                108,
                12,
                Component.translatable("gui.record_press_base.url_input"),
            )
        urlInput.setBordered(false)
        urlInput.setMaxLength(500)
        urlInput.value = be.urlTemplate
        urlInput.setResponder {
            lastModification = 0
        }
        addRenderableWidget(urlInput)

        // Record name input field (optional, for future use)
        nameInput =
            EditBox(
                font,
                x + 45,
                y + 50,
                108,
                12,
                Component.translatable("gui.record_press_base.name_input"),
            )
        nameInput.setMaxLength(100)
        nameInput.setBordered(false)
        nameInput.value = ""
        nameInput.setResponder {
            lastModification = 0
        }
        addRenderableWidget(nameInput)

        // Confirm button
        confirmButton = IconButton(x + background.width - 33, y + background.height - 24, AllIcons.I_CONFIRM)
        confirmButton.withCallback<IconButton> { onClose() }
        addRenderableWidget(confirmButton)
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

        // Render input backgrounds
        ModGuiTextures.RECORD_PRESS_BASE_INPUT.render(graphics, x + 42, y + 27)
        ModGuiTextures.RECORD_PRESS_BASE_INPUT.render(graphics, x + 42, y + 47)

        // Render icons
        ModGuiTextures.RECORD_PRESS_BASE_LINK_ICON.render(graphics, x + 21, y + 27)
        ModGuiTextures.RECORD_PRESS_BASE_NAME_ICON.render(graphics, x + 21, y + 47)

        GuiGameElement
            .of(renderedItem)
            .at<GuiGameElement.GuiRenderBuilder>(x + background.width + 6f, y + background.height - 56f, -200f)
            .scale(5.0)
            .render(graphics)
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

        urlInput.tick()
        nameInput.tick()
    }

    override fun removed() {
        sendPacket()
    }

    private fun sendPacket() {
        ModPackets.channel.sendToServer(
            ConfigureRecordPressBasePacket(
                be.blockPos,
                urlInput.value,
                nameInput.value,
            ),
        )
    }
}
