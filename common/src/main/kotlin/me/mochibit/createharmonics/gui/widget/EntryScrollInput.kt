package me.mochibit.createharmonics.gui.widget

import com.simibubi.create.foundation.gui.widget.SelectionScrollInput
import me.mochibit.createharmonics.foundation.locale.ModLang
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component



class EntryScrollInput(
    private val font: Font,
    x: Int, y: Int, width: Int, height: Int,
) : SelectionScrollInput(x, y, width, height) {

    private companion object {
        const val PAD = 4
    }

    private val newEntryLabel = ModLang.translate("gui.amethyst_catalyst.new_entry").component()
    private val hintText = ModLang.translate("gui.amethyst_catalyst.hint").component()

    private var programmatic = false
    private var draft = ""
    private var wasNew = true

    private val box = EditBox(font, x + PAD, y, width - PAD * 2, font.lineHeight, Component.empty()).apply {
        isBordered = false
        setMaxLength(2048)
        setResponder { if (!programmatic) onUserTyped(it) }
    }

    var entryNames: List<String> = emptyList()
        private set

    var busyLabel: String? = null

    var onUserTyped: (String) -> Unit = {}

    val isNewMode get() = state == 0
    val selectedEntryIndex get() = (state - 1).takeIf { it >= 0 }

    var text: String
        get() = box.value
        set(value) {
            programmatic = true
            box.value = value
            programmatic = false
        }

    init {
        applyOptions()
        titled(ModLang.translate("gui.amethyst_catalyst.entries").component().plainCopy())
    }


    fun setEntries(names: List<String>) {
        entryNames = names
        applyOptions()
    }

    fun selectNew() {
        setState(0)
        onChanged()
    }

    fun blur() {
        box.isFocused = false
    }

    private fun applyOptions() {
        forOptions(listOf(newEntryLabel) + entryNames.map(Component::literal))
        setState(state)
        syncBox()
    }

    private fun syncBox() {
        val nowNew = isNewMode
        if (wasNew && !nowNew) draft = box.value
        if (!wasNew && nowNew) text = draft
        if (!nowNew) box.isFocused = false
        wasNew = nowNew
    }

    private fun layoutBox() {
        box.x = x + PAD
        box.y = y + (height - font.lineHeight) / 2 + 1
    }

    override fun onChanged() {
        super.onChanged()
        syncBox()
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (!active) return false
        if (box.isFocused && box.value.isNotEmpty()) return false // non perdere la vista mentre scrivi
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!active || !isMouseOver(mouseX, mouseY)) {
            blur()
            return false
        }
        if (!isNewMode) selectNew()
        layoutBox()
        box.isFocused = true
        box.mouseClicked(mouseX, mouseY, button)
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean =
        box.isFocused && box.keyPressed(keyCode, scanCode, modifiers)

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean =
        box.isFocused && box.charTyped(codePoint, modifiers)
    

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        super.renderWidget(graphics, mouseX, mouseY, partialTicks)
        val textX = x + PAD
        val textY = y + (height - font.lineHeight) / 2 + 1
        val maxWidth = width - PAD * 2

        fun draw(s: String, color: Int) =
            graphics.drawString(font, font.plainSubstrByWidth(s, maxWidth), textX, textY, color)

        val busy = busyLabel
        when {
            busy != null -> draw(busy, 0xCCDDFF)
            !isNewMode -> draw(entryNames.getOrNull(state - 1).orEmpty(), 0xFFFFFF)
            box.value.isEmpty() && !box.isFocused -> draw(hintText.string, 0x888888)
            else -> {
                layoutBox()
                box.render(graphics, mouseX, mouseY, partialTicks)
            }
        }
    }
}