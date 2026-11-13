package me.mochibit.createharmonics.config.client

import me.mochibit.createharmonics.config.entries.ByteSizeAnnotation

import net.createmod.catnip.config.ui.ConfigHelper
import net.createmod.catnip.config.ui.ConfigTextField
import net.createmod.catnip.config.ui.entries.ValueEntry
import net.createmod.catnip.gui.UIRenderHelper
import net.createmod.catnip.gui.element.BoxElement
import net.createmod.catnip.gui.element.TextStencilElement
import net.createmod.catnip.gui.widget.BoxWidget
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.neoforged.neoforge.common.ModConfigSpec

/**
 * Byte unit
 * Enum — a fixed set of named constants.
 *
 * @constructor Creates a new ByteUnit
 * @property label
 * @property factor
 */
enum class ByteUnit(val label: String, val factor: Long) {
    /**
     * Kb
     *
     * @constructor Creates a new KB
     */
    KB("KB", 1L shl 10),

    /**
     * Mb
     *
     * @constructor Creates a new MB
     */
    MB("MB", 1L shl 20),

    /**
     * Gb
     *
     * @constructor Creates a new GB
     */
    GB("GB", 1L shl 30);

    /**
     * Next
     *
     * @return the byte unit
     */
    fun next(): ByteUnit = entries[(ordinal + 1) % entries.size]

    /**
     * Companion
     * Companion object — holds factory methods and constants for the enclosing class.
     *
     * @constructor Creates a new Companion
     */
    companion object {
        /**
         * Best for
         *
         * @param bytes
         * @return the byte unit
         */
        fun bestFor(bytes: Long): ByteUnit =
            entries.lastOrNull { bytes >= it.factor && bytes % it.factor == 0L } ?: KB
    }
}

/**
 * Byte size entry
 *
 * @constructor
 * @property valueSpec the value spec
 * @param label
 * @param value
 */
class ByteSizeEntry(
    label: String,
    value: ModConfigSpec.ConfigValue<Long>,
    private val valueSpec: ModConfigSpec.ValueSpec,
) : ValueEntry<Long>(label, value, valueSpec) {

    private var unit = ByteUnit.bestFor(getValue())
    private var updating = false

    private val font = Minecraft.getInstance().font
    private val amountField = ConfigTextField(font, 0, 0, 50, 16)
    private val unitText = TextStencilElement(font, unit.label).centered(true, true)
    private val unitButton: BoxWidget

    init {
        amountField.setFilter { s -> s.length <= 10 && s.all(Char::isDigit) }
        amountField.setResponder(::onAmountTyped)

        unitText.withElementRenderer<TextStencilElement> { ms, w, h, _ ->
            UIRenderHelper.angledGradient(ms, 0F, 0, h / 2, h.toFloat(), w.toFloat(), UIRenderHelper.COLOR_TEXT)
        }
        unitButton = BoxWidget(0, 0, UNIT_WIDTH, 16)
            .withCustomBackground<BoxWidget>(BoxElement.COLOR_BACKGROUND_FLAT)
            .showingElement<BoxWidget>(unitText)
            .withCallback(Runnable { cycleUnit() })

        listeners.add(amountField)
        listeners.add(unitButton)

        onReset()
    }

    private fun toBytes(amount: Long): Long? =
        try { Math.multiplyExact(amount, unit.factor) } catch (_: ArithmeticException) { null }

    private fun onAmountTyped(text: String) {
        if (updating) return
        validateAndCommit(text.toLongOrNull())
    }

    private fun validateAndCommit(amount: Long?) {
        val bytes = amount?.let(::toBytes)
        if (bytes == null || !valueSpec.test(bytes)) {
            amountField.setTextColor(ERROR_COLOR)
            return
        }
        amountField.setTextColor(OK_COLOR)
        updating = true
        try {
            setValue(bytes)
            bumpCog(10f)
        } finally {
            updating = false
        }
    }

    private fun cycleUnit() {
        unit = unit.next()
        unitText.withText(unit.label)
        validateAndCommit(amountField.value.toLongOrNull())
    }

    override fun onValueChange(newValue: Long) {
        super.onValueChange(newValue)
        if (updating) return

        unit = ByteUnit.bestFor(newValue)
        unitText.withText(unit.label)
        updating = true
        try {
            amountField.value = (newValue / unit.factor).toString()
        } finally {
            updating = false
        }
        amountField.setTextColor(OK_COLOR)
    }

    override fun setEditable(b: Boolean) {
        super.setEditable(b)
        amountField.setEditable(b)
        unitButton.active = b
        unitButton.animateGradientFromState()
    }

    override fun tick() {
        super.tick()
        unitButton.tick()
    }

    override fun render(
        graphics: GuiGraphics, index: Int, y: Int, x: Int, width: Int, height: Int,
        mouseX: Int, mouseY: Int, hovered: Boolean, partialTicks: Float,
    ) {
        super.render(graphics, index, y, x, width, height, mouseX, mouseY, hovered, partialTicks)

        val right = x + width - resetWidth - 16
        unitButton.setX(right - UNIT_WIDTH)
        unitButton.setY(y + 10)
        unitButton.render(graphics, mouseX, mouseY, partialTicks)

        val fieldX = x + getLabelWidth(width) + 4
        amountField.setX(fieldX)
        amountField.setY(y + 10)
        amountField.width = unitButton.x - 4 - fieldX
        amountField.render(graphics, mouseX, mouseY, partialTicks)
    }

    /**
     * Companion
     * Companion object — holds factory methods and constants for the enclosing class.
     *
     * @constructor Creates a new Companion
     */
    companion object {
        private const val UNIT_WIDTH = 40
        private const val OK_COLOR = 0xFFFFFF
        private const val ERROR_COLOR = 0xFF5555

        /**
         * Accepts
         *
         * @param configValue the config value
         * @param valueSpec the value spec
         * @return `true` if the condition holds, `false` otherwise
         */
        @JvmStatic
        fun accepts(configValue: ModConfigSpec.ConfigValue<*>, valueSpec: ModConfigSpec.ValueSpec): Boolean {
            if (configValue !is ModConfigSpec.LongValue) return false
            val lines = valueSpec.comment?.split('\n')?.toMutableList() ?: return false
            return ByteSizeAnnotation.NAME in ConfigHelper.readMetadataFromComment(lines).second
        }
    }
}