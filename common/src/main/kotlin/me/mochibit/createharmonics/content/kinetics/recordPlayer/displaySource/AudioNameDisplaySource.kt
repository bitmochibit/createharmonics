package me.mochibit.createharmonics.content.kinetics.recordPlayer.displaySource

import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats
import com.simibubi.create.content.trains.display.FlapDisplaySection
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBehaviour
import me.mochibit.createharmonics.foundation.locale.ModLang
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

class AudioNameDisplaySource : SingleLineDisplaySource() {
    private fun String.codePointArray(): IntArray = codePoints().toArray()

    private fun IntArray.codePointSubstring(
        start: Int,
        end: Int,
    ): String {
        val s = start.coerceIn(0, size)
        val e = end.coerceIn(s, size)
        return String(this, s, e - s)
    }

    private fun String.safeTake(n: Int) = codePointArray().codePointSubstring(0, n)

    private fun String.safeChunked(size: Int): List<String> {
        val cps = codePointArray()
        return (cps.indices step size).map { i ->
            String(cps, i, minOf(size, cps.size - i))
        }
    }

    private fun String.codePointLength(): Int = codePointCount(0, length)

    private fun getDisplayMode(context: DisplayLinkContext): Int = context.sourceConfig().getInt("DisplayMode")

    override fun provideLine(
        context: DisplayLinkContext,
        stats: DisplayTargetStats,
    ): MutableComponent {
        val smartBe = context.sourceBlockEntity as? SmartBlockEntity ?: return EMPTY_LINE
        val audioPlayerBehaviour =
            smartBe.getBehaviour(RecordPlayerBehaviour.BEHAVIOUR_TYPE) ?: return EMPTY_LINE

        if (!audioPlayerBehaviour.hasRecord()) {
            return ModLang.translate("display_source.no_record").component()
        }

        val currentBeTitle =
            audioPlayerBehaviour.audioPlayingTitle
                ?: return ModLang.translate("display_source.unknown_audio_name").component()

        val title =
            currentBeTitle.ifEmpty {
                ModLang.translate("display_source.loading_title").string()
            }

        val label = context.sourceConfig().getString("Label")

        val labelSize = if (label.isEmpty()) 0 else label.codePointLength() + 1
        val maxCols = (stats.maxColumns() - labelSize).coerceAtLeast(1)

        if (title.codePointLength() <= maxCols) return Component.literal(title)

        val gameTime = smartBe.level?.gameTime ?: return Component.literal(title.safeTake(maxCols))

        return when (getDisplayMode(context)) {
            1 -> provideScrolled(title, maxCols, context, gameTime)
            2 -> provideWrapped(title, maxCols, context, gameTime)
            else -> Component.literal(title.safeTake((maxCols - 1).coerceAtLeast(1)))
        }
    }

    private fun provideScrolled(
        title: String,
        maxCols: Int,
        context: DisplayLinkContext,
        gameTime: Long,
    ): MutableComponent {
        val cfg = context.sourceConfig()

        if (cfg.getString("LastTitle") != title) {
            cfg.putString("LastTitle", title)
            cfg.putInt("Tick", 0)
            cfg.putLong("LastGameTick", -1L)
        }

        if (cfg.getLong("LastGameTick") != gameTime) {
            cfg.putLong("LastGameTick", gameTime)
            cfg.putInt("Tick", cfg.getInt("Tick") + 1)
        }

        val tick = cfg.getInt("Tick")
        val padded = title + SCROLL_SEPARATOR
        val cps = padded.codePointArray()
        val offset = (tick / SCROLL_SPEED) % cps.size
        val end = offset + maxCols
        val visible =
            if (end <= cps.size) {
                cps.codePointSubstring(offset, end)
            } else {
                cps.codePointSubstring(offset, cps.size) + cps.codePointSubstring(0, end - cps.size)
            }

        return Component.literal(visible)
    }

    private fun provideWrapped(
        title: String,
        maxCols: Int,
        context: DisplayLinkContext,
        gameTime: Long,
    ): MutableComponent {
        val cfg = context.sourceConfig()

        if (cfg.getString("LastTitle") != title) {
            cfg.putString("LastTitle", title)
            cfg.putInt("Tick", 0)
            cfg.putLong("LastGameTick", -1L)
        }

        if (cfg.getLong("LastGameTick") != gameTime) {
            cfg.putLong("LastGameTick", gameTime)
            cfg.putInt("Tick", cfg.getInt("Tick") + 1)
        }

        val tick = cfg.getInt("Tick")
        val chunkSize = (maxCols - 1).coerceAtLeast(1)
        val pages = title.safeChunked(chunkSize)
        val pageIndex = (tick / WRAP_HOLD) % pages.size

        return Component.literal(pages[pageIndex])
    }

    override fun getTranslationKey(): String = "read_music_name"

    override fun allowsLabeling(context: DisplayLinkContext): Boolean = true

    override fun getPassiveRefreshTicks(): Int = 20

    override fun initConfigurationWidgets(
        context: DisplayLinkContext,
        builder: ModularGuiLineBuilder,
        isFirstLine: Boolean,
    ) {
        super.initConfigurationWidgets(context, builder, isFirstLine)
        if (isFirstLine) return
        builder.addSelectionScrollInput(
            0,
            90,
            { si, _ ->
                si
                    .forOptions(
                        ModLang.translatedOptions(
                            "display_source.audio_name",
                            "full",
                            "scroll",
                            "wrap",
                        ),
                    ).titled(ModLang.translate("display_source.audio_name.display").component())
            },
            "DisplayMode",
        )
    }

    override fun getFlapDisplayLayoutName(context: DisplayLinkContext): String = "Instant"

    override fun createSectionForValue(
        context: DisplayLinkContext,
        size: Int,
    ): FlapDisplaySection = FlapDisplaySection(size.toFloat() * 7.0f, "instant", false, false)

    companion object {
        private const val SCROLL_SPEED = 1
        private const val WRAP_HOLD = 10
        private const val SCROLL_SEPARATOR = "      "
    }
}
