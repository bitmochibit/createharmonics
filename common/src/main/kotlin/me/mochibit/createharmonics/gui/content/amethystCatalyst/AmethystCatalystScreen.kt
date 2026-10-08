package me.mochibit.createharmonics.gui.content.amethystCatalyst

import com.simibubi.create.foundation.gui.AllIcons
import com.simibubi.create.foundation.gui.widget.IconButton
import me.mochibit.createharmonics.audio.upload.AudioEntryInfo
import me.mochibit.createharmonics.audio.upload.ClientAudioLibrary
import me.mochibit.createharmonics.audio.upload.ClientAudioUpload
import me.mochibit.createharmonics.content.uploader.AmethystCatalystBlock
import me.mochibit.createharmonics.content.uploader.AmethystCatalystBlockEntity
import me.mochibit.createharmonics.foundation.extension.toMultilineComponent
import me.mochibit.createharmonics.foundation.locale.ModLang
import me.mochibit.createharmonics.foundation.network.packet.DeleteAudioPacket
import me.mochibit.createharmonics.foundation.registry.ModBlocks
import me.mochibit.createharmonics.foundation.registry.ModIcons
import me.mochibit.createharmonics.foundation.services.networkService
import me.mochibit.createharmonics.gui.ModGuiTexture
import me.mochibit.createharmonics.gui.widget.AdvancedIconButton
import me.mochibit.createharmonics.gui.widget.EntryScrollInput
import net.createmod.catnip.gui.AbstractSimiScreen
import net.createmod.catnip.gui.element.GuiGameElement
import net.createmod.catnip.gui.element.ScreenElement
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.component.DataComponents
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.BlockItemStateProperties
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.nio.file.Files
import java.nio.file.Path

class AmethystCatalystScreen(
    private val be: AmethystCatalystBlockEntity,
) : AbstractSimiScreen(ModLang.translate("gui.amethyst_catalyst.title").component()) {

    private companion object {
        const val ROW_Y = 23
        const val FOLDER_X = 20
        const val PICKER_X = 47
        const val PICKER_W = 91
        const val PICKER_H = 18
        const val REFRESH_X = 160
        const val ACTION_X = 12
        const val ACTION_Y = 55

        const val CRYSTAL_U = 4
        const val CRYSTAL_V = 84
        const val CRYSTAL_X = 35
        const val CRYSTAL_Y = 56
        const val CRYSTAL_W = 2
        const val CRYSTAL_H = 16
    }

    private sealed interface Source {
        data object None : Source
        data class Local(val file: Path) : Source
        data class Url(val url: String) : Source
        data class Existing(val entry: AudioEntryInfo) : Source
    }

    private data class ActionUi(
        val icon: ScreenElement,
        val tooltipKey: String,
        val enabled: Boolean,
    )

    // textures
    private val background = ModGuiTexture("amethyst_catalyst", 0, 0, 166, 79)
    private val crystalTexture = ModGuiTexture("amethyst_catalyst", CRYSTAL_U, CRYSTAL_V, CRYSTAL_W, CRYSTAL_H)
    private val renderedItem = ItemStack(ModBlocks.AMETHYST_CATALYST.get())

    // state
    private lateinit var picker: EntryScrollInput
    private lateinit var folderButton: IconButton
    private lateinit var refreshButton: IconButton
    private lateinit var actionButton: IconButton

    private var pickedFile: Path? = null
    private var lastEntries: List<AudioEntryInfo>? = null
    private var lastActionUi: ActionUi? = null
    private var wasUploading = false

    private val entries get() = ClientAudioLibrary.entries

    private val source: Source
        get() = when (val i = picker.selectedEntryIndex) {
            null -> pickedFile?.let(Source::Local)
                ?: picker.text.trim().takeIf { it.isNotEmpty() }?.let(Source::Url)
                ?: Source.None

            else -> entries.getOrNull(i)?.let(Source::Existing) ?: Source.None
        }

    // init

    override fun init() {
        setWindowSize(background.width, background.height)
        setWindowOffset(-20, 0)
        super.init()
        clearWidgets()

        if (!::picker.isInitialized) {
            picker = EntryScrollInput(font, 0, 0, PICKER_W, PICKER_H).also {
                it.onUserTyped = { pickedFile = null }
            }
        }
        picker.x = guiLeft + PICKER_X
        picker.y = guiTop + ROW_Y
        addRenderableWidget(picker)

        AdvancedIconButton(guiLeft + background.width - 33, guiTop + ACTION_Y, AllIcons.I_CONFIRM).apply {
            withCallback<IconButton> { minecraft?.player?.closeContainer() }
            addRenderableWidget(this)
        }

        folderButton = AdvancedIconButton(guiLeft + FOLDER_X, guiTop + ROW_Y, AllIcons.I_OPEN_FOLDER).apply {
            withCallback<IconButton> { pickFile() }
            setToolTip(ModLang.translate("gui.amethyst_catalyst.pick_file").component())
            addRenderableWidget(this)
        }
        refreshButton = AdvancedIconButton(guiLeft + REFRESH_X, guiTop + ROW_Y, AllIcons.I_REFRESH).apply {
            withCallback<IconButton> { ClientAudioLibrary.refresh() }
            setToolTip(ModLang.translate("gui.amethyst_catalyst.refresh").component())
            addRenderableWidget(this)
        }
        actionButton = AdvancedIconButton(guiLeft + ACTION_X, guiTop + ACTION_Y, ModIcons.I_UPLOAD).apply {
            withCallback<IconButton> { runAction() }
            addRenderableWidget(this)
        }
        lastActionUi = null

        ClientAudioLibrary.refresh()
        updateWidgets()
    }

    // actions

    private fun runAction() {
        if (!be.hasCrystal) return
        when (val s = source) {
            is Source.Local -> {
                if (ClientAudioUpload.isBusy) return
                if (!Files.isReadable(s.file)) return
                ClientAudioUpload.begin(ClientAudioUpload.PendingUpload(s.file, be.blockPos))
                pickedFile = null
                picker.text = ""
            }

            is Source.Url -> TODO("yt-dlp")
            is Source.Existing -> {
                networkService.sendToServer(DeleteAudioPacket(be.blockPos, s.entry.fileId))
                picker.selectNew()
            }

            Source.None -> Unit
        }
    }

    private fun pickFile() {
        Thread({
            val chosen = MemoryStack.stackPush().use { stack ->
                val filters = stack.mallocPointer(3)
                listOf("*.mp3", "*.ogg", "*.wav").forEach { filters.put(stack.UTF8(it)) }
                filters.flip()
                TinyFileDialogs.tinyfd_openFileDialog("Audio", null, filters, "Audio files", false)
            }
            chosen?.let { path ->
                Minecraft.getInstance().execute {
                    val file = Path.of(path)
                    picker.selectNew()
                    picker.text = file.fileName.toString()
                    pickedFile = file
                }
            }
        }, "createharmonics-file-picker").apply { isDaemon = true }.start()
    }

    // tick

    private fun updateWidgets() {
        val hasCrystal = be.hasCrystal
        val busy = be.isUploading || ClientAudioUpload.isBusy
        val usable = hasCrystal && !busy

        if (!hasCrystal) picker.blur()
        picker.visible = hasCrystal
        picker.active = usable
        picker.busyLabel = if (be.isUploading) be.uploadingFileName else null

        folderButton.active = usable
        refreshButton.active = usable
        applyActionUi(actionUi(source, usable))
    }

    override fun tick() {
        super.tick()
        val uploading = be.isUploading
        if (wasUploading && !uploading) ClientAudioLibrary.refresh()
        wasUploading = uploading

        syncEntries()
        updateWidgets()
    }

    private fun syncEntries() {
        val current = entries
        if (current === lastEntries) return
        lastEntries = current
        picker.setEntries(current.map { it.displayName })
    }

    private fun actionUi(source: Source, enabled: Boolean) = when (source) {
        is Source.Local, is Source.Url -> ActionUi(ModIcons.I_UPLOAD, "upload", enabled)
        is Source.Existing -> ActionUi(AllIcons.I_TRASH, "delete", enabled)
        Source.None -> lastActionUi?.copy(enabled = false)
            ?: ActionUi(ModIcons.I_UPLOAD, "upload", false)
    }

    private fun applyActionUi(ui: ActionUi) {
        if (ui != lastActionUi) {
            if (ui.icon != lastActionUi?.icon) actionButton.setIcon(ui.icon)
            if (ui.tooltipKey != lastActionUi?.tooltipKey) {
                actionButton.toolTip.clear()
                actionButton.toolTip.addAll(
                    ModLang.translate("gui.amethyst_catalyst.${ui.tooltipKey}").component().toMultilineComponent(),
                )
            }
            lastActionUi = ui
        }
        actionButton.active = ui.enabled
    }

    // render

    override fun renderWindow(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        val x = guiLeft
        val y = guiTop
        background.render(graphics, x, y)
        graphics.drawString(font, title, x + background.width / 2 - font.width(title) / 2, y + 4, 0x592424, false)

        if (be.hasCrystal) {
            graphics.blit(
                crystalTexture.location,
                x + CRYSTAL_X, y + CRYSTAL_Y,
                CRYSTAL_U.toFloat(), CRYSTAL_V.toFloat(),
                CRYSTAL_W, CRYSTAL_H,
                256, 256,
            )
        }


        GuiGameElement.of(renderedItem)
            .at<GuiGameElement.GuiRenderBuilder>(x + background.width + 6f, y + background.height - 56f, -200f)
            .scale(5.0)
            .render(graphics)
    }

    // input

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!picker.isMouseOver(mouseX, mouseY)) picker.blur()
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean =
        (be.hasCrystal && picker.keyPressed(keyCode, scanCode, modifiers)) ||
                super.keyPressed(keyCode, scanCode, modifiers)

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean =
        (be.hasCrystal && picker.charTyped(codePoint, modifiers)) ||
                super.charTyped(codePoint, modifiers)
}

