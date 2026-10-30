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


data class EntryScrollInputConfig(
    val autoSelectFirst: Boolean = false,
    val selfRenderTooltip: Boolean = true,
    val showPlaceholderEmpty: Boolean = true,
    val showPlaceholderHint: Boolean = true,
    val showTooltipHint: Boolean = true,
    val texts: Texts = Texts(),
) {
    data class Texts(
        val placeholderHint: Component = default("placeholder_hint"),
        val placeholderEmpty: Component = default("placeholder_empty"),

        // hover
        val tooltipTitle: Component = default("tooltip_title"),
        val tooltipScrollToSelect: Component = default("tooltip_scroll_to_select"),
        val tooltipHint: List<Component> = emptyList(),

        val tooltipEmpty: Component = default("tooltip_empty"),
        val tooltipEmptyInstructions: List<Component> = emptyList(),
    )
}

private fun default(key: String): Component =
    ModLang.translate("gui.entry_scroll_input.$key").component()

/**
 * A specialized scroll input component that combines a dropdown-like selection list
 * with a free-form text input box.
 *
 * Behavior can be customized via [Configurable][EntryScrollInputConfig] to handle placeholders,
 * tooltips, and auto-selection rules.
 *
 * ### It allows:
 * - **Scroll Selection**: Scroll through a list of entries or use the tooltip preview.
 * - **Free Typing / New Mode**: Type custom text freely when no entry is selected.
 * - **Edit Mode**: Allows in-place renaming of existing selected entries.
 * - **Flexible Binding**: Selection updates can be handled both programmatically and manually by the user.
 *
 * @property font The font renderer used for text and placeholder display.
 * @param x The X coordinate of the widget on the screen.
 * @param y The Y coordinate of the widget on the screen.
 * @param width The total width of the widget.
 * @param height The total height of the widget.
 * @property config The [Configurable][EntryScrollInputConfig] instance defining texts and behavior rules.
 */
class EntryScrollInput(
    private val font: Font,
    x: Int, y: Int, width: Int, height: Int,
    val config: EntryScrollInputConfig = EntryScrollInputConfig(),
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
        setMaxLength(MAX_INPUT_LENGTH)
        setResponder { if (!programmatic) onUserTyped(it) }
    }

    var entryNames: List<String> = emptyList()
        private set

    private var autoSelectConsumed = false

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

    val hoverTooltip: List<Component> get() = toolTip


    init {
        applyOptions()
        titled(config.texts.tooltipTitle.plainCopy())
    }

    fun setEntries(names: List<String>) {
        if (names == entryNames) return
        entryNames = names
        applyOptions()
    }

    fun select(index: Int?) {
        autoSelectConsumed = true
        selected = index?.takeIf { it in entryNames.indices }
        selected?.let { setState(it) }
        syncBox()
    }

    fun selectNew() {
        autoSelectConsumed = true
        selected = null
        syncBox()
        onSelected()
    }

    fun rearmAutoSelect() {
        autoSelectConsumed = false
        if (maybeAutoSelect()) {
            setState(0)
            syncBox()
            onSelected()
        }
    }

    private fun maybeAutoSelect(): Boolean {
        if (!config.autoSelectFirst || autoSelectConsumed || selected != null ||
            entryNames.isEmpty() || isTyping || text.isNotEmpty()
        ) return false
        autoSelectConsumed = true
        selected = 0
        return true
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

        val autoSelected = maybeAutoSelect()

        selected?.let { setState(it) }
        syncBox()
        updateTooltip()
        if (autoSelected) onSelected()
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

        fun drawBox() {
            layoutBox()
            box.render(graphics, mouseX, mouseY, partialTicks)
        }

        val busy = busyLabel
        when {
            busy != null -> draw(busy, 0xCCDDFF)
            editing -> drawBox()
            !isNewMode -> draw(entryNames.getOrNull(selected!!).orEmpty(), 0xFFFFFF)
            box.value.isEmpty() && !box.isFocused -> {
                val placeholder = when {
                    entryNames.isEmpty() && config.showPlaceholderEmpty -> config.texts.placeholderEmpty
                    config.showPlaceholderHint -> config.texts.placeholderHint
                    else -> null
                }
                placeholder?.let { draw(it.string, 0x888888) }
            }
            else -> drawBox()
        }
    }

    override fun updateTooltip() {
        toolTip.clear()
        val header = title ?: return
        val texts = config.texts

        toolTip.add(header.plainCopy().withStyle { s: Style -> s.withColor(HEADER_RGB.rgb) })

        if (entryNames.isEmpty()) {
            toolTip.add(texts.tooltipEmpty.plainCopy().withStyle(ChatFormatting.GRAY))
            texts.tooltipEmptyInstructions.forEach {
                toolTip.add(it.plainCopy().withStyle { s: Style -> s.withColor(HINT_RGB.rgb) })
            }
            return
        }

        var min = min(this.max - 16, state - 7)
        var max = max(this.min + 16, state + 8)
        min = max(min, this.min)
        max = min(max, this.max)
        if (this.min + 1 == min) min--
        if (min > this.min) toolTip.add(Component.literal("> ...").withStyle(ChatFormatting.GRAY))
        if (this.max - 1 == max) max++
        for (i in min..<max) {
            toolTip.add(
                Component.empty()
                    .append(if (i == state) "-> " else "> ")
                    .append(options[i])
                    .withStyle(if (i == state) ChatFormatting.WHITE else ChatFormatting.GRAY)
            )
        }
        if (max < this.max) toolTip.add(Component.literal("> ...").withStyle(ChatFormatting.GRAY))

        if (config.showTooltipHint) texts.tooltipHint.forEach {
            toolTip.add(it.plainCopy().withStyle { s: Style -> s.withColor(HINT_RGB.rgb) })
        }
        toolTip.add(
            texts.tooltipScrollToSelect.plainCopy()
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
        )
    }

    override fun renderTooltip(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        if (config.selfRenderTooltip) super.renderTooltip(graphics, mouseX, mouseY, partialTicks)
    }
}