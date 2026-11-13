package me.mochibit.createharmonics.content.kinetics.recordPlayer

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions
import com.simibubi.create.foundation.gui.AllIcons
import me.mochibit.createharmonics.foundation.registry.ModIcons

/**
 * Playback mode
 * Enum — a fixed set of named constants.
 *
 * @constructor Creates a new PlaybackMode
 * @property icon
 * @property isPause the is pause
 * @property isStaticPitch the is static pitch
 */
enum class PlaybackMode(
    private val icon: AllIcons, val isPause: Boolean, val isStaticPitch: Boolean,
) : INamedIconOptions {
    /**
     * Play
     *
     * @constructor Creates a new PLAY
     */
    PLAY(AllIcons.I_PLAY, false, false),

    /**
     * Pause
     *
     * @constructor Creates a new PAUSE
     */
    PAUSE(AllIcons.I_PAUSE, true, false),

    /**
     * Play static pitch
     *
     * @constructor Creates a new PLAY_STATIC_PITCH
     */
    PLAY_STATIC_PITCH(ModIcons.I_PLAY_PITCH_STATIC, false, true),

    /**
     * Pause static pitch
     *
     * @constructor Creates a new PAUSE_STATIC_PITCH
     */
    PAUSE_STATIC_PITCH(ModIcons.I_PAUSE_PITCH_STATIC, true, true);

    private val translationKey = "createharmonics.record_player.playback_mode.${name.lowercase()}"
    override fun getIcon() = icon
    override fun getTranslationKey() = translationKey

    /**
     * Companion
     * Companion object — holds factory methods and constants for the enclosing class.
     *
     * @constructor Creates a new Companion
     */
    companion object {
        /**
         * Returns the scroll value
         *
         * @param v
         * @return the playback mode
         */
        fun fromScrollValue(v: Int) = entries.getOrElse(v) { PLAY }
    }
}