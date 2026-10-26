package me.mochibit.createharmonics.gui.widget

import com.simibubi.create.AllSoundEvents
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput
import me.mochibit.createharmonics.audio.upload.MAX_DISPLAY_NAME_LENGTH
import me.mochibit.createharmonics.foundation.locale.ModLang
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min


/**
 * Entry scroll which lets you write or choose options
 */
class EntryScrollInput(
    private val font: Font,
    x: Int, y: Int, width: Int, height: Int,
    val titleTooltip: MutableComponent = ModLang.translate("gui.entryScrollInput.title").component(),
    val scrollToSelectTooltip: MutableComponent = ModLang.translate("gui.entryScrollInput.scrollToSelect").component(),
    val noSelectableEntriesTooltip: MutableComponent = ModLang.translate("gui.entryScrollInput.noSelectableEntries")
        .component(),
    val hintTooltip: MutableComponent = ModLang.translate("gui.entryScrollInput.hintTooltip").component()
) : SelectionScrollInput(x, y, width, height) {

    private companion object {
        const val PAD = 4
        const val MAX_INPUT_LENGTH = 2048
    }

    private var programmatic = false
    private var draft = ""
    private var wasNew = true

    private var selected: Int? = null

    private val box = EditBox(font, x + PAD, y, width - PAD * 2, font.lineHeight, Component.empty()).apply {
        isBordered = false
        setMaxLength(2048)
        setResponder { if (!programmatic) onUserTyped(it) }
    }

    var entryNames: List<String> = emptyList()
        private set

    private var editing = false
    val isEditing get() = editing
    var onRenamed: (index: Int, newName: String) -> Unit = { _, _ -> }

    var busyLabel: String? = null
    var onSelected: () -> Unit = {}
    var onUserTyped: (String) -> Unit = {}

    val isTyping get() = box.isFocused
    val isNewMode get() = selected == null
    val selectedEntryIndex get() = selected

    var text: String
        get() = box.value
        set(value) {
            programmatic = true
            box.value = value
            programmatic = false
        }

    init {
        applyOptions()
        titled(titleTooltip.plainCopy())
    }

    fun setEntries(names: List<String>) {
        entryNames = names
        applyOptions()
    }

    fun select(index: Int?) {
        selected = index?.takeIf { it in entryNames.indices }
        selected?.let { setState(it) }
        syncBox()
    }

    fun selectNew() {
        selected = null
        syncBox()
        onSelected()
    }

    fun blur() = commitEdit().also { box.isFocused = false }

    fun beginEdit() {
        val index = selected ?: return
        editing = true
        box.setMaxLength(MAX_DISPLAY_NAME_LENGTH)
        text = entryNames[index]
        layoutBox()
        box.isFocused = true
        box.setCursorPosition(box.value.length)
        box.setHighlightPos(0)
    }

    fun commitEdit() {
        if (!editing) return
        val index = selected
        val newName = box.value.trim()
        endEdit()
        if (index != null && newName != entryNames[index]) {
            onRenamed(index, newName)
            if (newName.isNotEmpty()) setEntries(entryNames.toMutableList().also { it[index] = newName })
        }
    }

    private fun endEdit() {
        editing = false
        box.isFocused = false
        box.setMaxLength(MAX_INPUT_LENGTH)
    }

    private fun applyOptions() {
        forOptions(entryNames.map(Component::literal))
        if (selected?.let { it !in entryNames.indices } == true) selected = null
        selected?.let { setState(it) }
        syncBox()
    }

    private fun syncBox() {
        val nowNew = isNewMode
        if (nowNew && editing) endEdit()
        if (wasNew && !nowNew) draft = box.value
        if (!wasNew && nowNew) text = draft
        if (!nowNew && !editing) box.isFocused = false
        wasNew = nowNew
    }

    private fun layoutBox() {
        box.x = x + PAD
        box.y = y + (height - font.lineHeight) / 2 + 1
        box.setWidth(width - PAD * 2)
    }

    private fun playScrollSound() {
        if (soundPlayed) return
        soundPlayed = true
        Minecraft.getInstance().soundManager.play(
            SimpleSoundInstance.forUI(AllSoundEvents.SCROLL_VALUE.mainEvent, 1.5f),
        )
    }

    override fun onChanged() {
        super.onChanged()
        selected = state
        syncBox()
        onSelected()
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (!active || editing || entryNames.isEmpty()) return false
        if (box.isFocused && box.value.isNotEmpty()) return false
        if (selected == null) {
            selected = 0
            setState(0)
            syncBox()
            playScrollSound()
            onSelected()
            return true
        }
        super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!active || !isMouseOver(mouseX, mouseY)) return false
        if (editing) {
            layoutBox()
            box.mouseClicked(mouseX, mouseY, button)
            return true
        }
        if (!isNewMode) selectNew()
        layoutBox()
        box.isFocused = true
        box.mouseClicked(mouseX, mouseY, button)
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (editing && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            commitEdit()
            return true
        }
        return box.isFocused && box.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean =
        box.isFocused && box.charTyped(codePoint, modifiers)

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        super.renderWidget(graphics, mouseX, mouseY, partialTicks)
        val textX = x + PAD
        val textY = y + (height - font.lineHeight) / 2 + 1
        val maxWidth = width - PAD * 2

        fun draw(s: String, color: Int) =
            graphics.drawString(font, font.plainSubstrByWidth(s, maxWidth), textX, textY, color)

        fun drawBox() { layoutBox(); box.render(graphics, mouseX, mouseY, partialTicks) }

        val busy = busyLabel
        when {
            busy != null -> draw(busy, 0xCCDDFF)
            editing -> drawBox()
            !isNewMode -> draw(entryNames.getOrNull(selected!!).orEmpty(), 0xFFFFFF)
            box.value.isEmpty() && !box.isFocused ->
                draw((if (entryNames.isEmpty()) noSelectableEntriesTooltip else hintTooltip).string, 0x888888)
            else -> drawBox()
        }
    }


    override fun updateTooltip() {
        toolTip.clear()
        if (title == null) return
        toolTip.add(
            title.plainCopy()
                .withStyle { s: Style -> s.withColor(HEADER_RGB.getRGB()) }
        )
        var min = min(this.max - 16, state - 7)
        var max = max(this.min + 16, state + 8)
        min = max(min, this.min)
        max = min(max, this.max)
        if (this.min + 1 == min) min--
        if (min > this.min) {
            toolTip.add(
                Component.literal("> ...")
                    .withStyle(ChatFormatting.GRAY)
            )
        }
        if (this.max - 1 == max) max++
        for (i in min..<max) {
            if (i == state) toolTip.add(
                Component.empty()
                    .append("-> ")
                    .append(options.get(i))
                    .withStyle(ChatFormatting.WHITE)
            )
            else toolTip.add(
                Component.empty()
                    .append("> ")
                    .append(options.get(i))
                    .withStyle(ChatFormatting.GRAY)
            )
        }
        if (max < this.max) {
            toolTip.add(
                Component.literal("> ...")
                    .withStyle(ChatFormatting.GRAY)
            )
        }

        if (hint != null) toolTip.add(
            hint.plainCopy()
                .withStyle { s: Style -> s.withColor(HINT_RGB.getRGB()) }
        )
        toolTip.add(
            scrollToSelectTooltip.plainCopy()
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
        )
    }


}