package me.mochibit.createharmonics.content.kinetics.recordPlayer

import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import me.mochibit.createharmonics.registry.ModLang
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

class AudioNameDisplaySource : SingleLineDisplaySource() {
    override fun provideLine(
        context: DisplayLinkContext,
        stats: DisplayTargetStats,
    ): MutableComponent {
        val smartBe = context.sourceBlockEntity as? SmartBlockEntity ?: return EMPTY_LINE
        val audioPlayerBehaviour = smartBe.getBehaviour(RecordPlayerBehaviour.BEHAVIOUR_TYPE) ?: return EMPTY_LINE

        if (!audioPlayerBehaviour.hasRecord()) {
            return ModLang.translate("display_source.no_record").component()
        }

        val title =
            audioPlayerBehaviour.audioPlayingTitle ?: return ModLang
                .translate("display_source.unknown_audio_name")
                .component()

        return Component.literal(title)
    }

    override fun getTranslationKey(): String = "read_music_name"

    override fun allowsLabeling(context: DisplayLinkContext): Boolean = true
}
