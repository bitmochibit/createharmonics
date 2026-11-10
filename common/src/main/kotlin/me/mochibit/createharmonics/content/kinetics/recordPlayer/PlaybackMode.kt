package me.mochibit.createharmonics.content.kinetics.recordPlayer

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions
import com.simibubi.create.foundation.gui.AllIcons
import me.mochibit.createharmonics.foundation.registry.ModIcons

enum class PlaybackMode(
    private val icon: AllIcons, val isPause: Boolean, val isStaticPitch: Boolean,
) : INamedIconOptions {
    PLAY(AllIcons.I_PLAY, false, false),
    PAUSE(AllIcons.I_PAUSE, true, false),
    PLAY_STATIC_PITCH(ModIcons.I_PLAY_PITCH_STATIC, false, true),
    PAUSE_STATIC_PITCH(ModIcons.I_PAUSE_PITCH_STATIC, true, true);

    private val translationKey = "createharmonics.record_player.playback_mode.${name.lowercase()}"
    override fun getIcon() = icon
    override fun getTranslationKey() = translationKey

    companion object {
        fun fromScrollValue(v: Int) = entries.getOrElse(v) { PLAY }
    }
}