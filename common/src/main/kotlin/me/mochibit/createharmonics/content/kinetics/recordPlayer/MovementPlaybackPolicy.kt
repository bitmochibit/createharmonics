package me.mochibit.createharmonics.content.kinetics.recordPlayer

import kotlin.math.abs


/**
 * Playback gate
 *
 * @constructor Creates a new PlaybackGate
 * @property graceTicks the grace ticks
 */
class PlaybackGate(private val graceTicks: Int = 20) {
    private var pending: PlaybackState? = null
    private var remaining = 0


    /**
     * Next
     *
     * @param current
     * @param target
     * @param immediate
     * @return the playback state
     */
    fun next(current: PlaybackState, target: PlaybackState, immediate: Boolean = false): PlaybackState? {
        if (target == current) { pending = null; return null }
        if (target == PlaybackState.PLAYING || immediate) { pending = null; return target }
        if (pending != target) { pending = target; remaining = graceTicks }
        if (--remaining > 0) return null
        pending = null
        return target
    }
}

/**
 * Movement playback policy
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new MovementPlaybackPolicy
 */
object MovementPlaybackPolicy {
    private const val SPEED_THRESHOLD = 0.01f

    /**
     * Target
     *
     * @param disassembled
     * @param hasRecord the has record
     * @param disabled
     * @param animationSpeed the animation speed
     * @param stalled
     * @param pauseModeWithRedstone the pause mode with redstone
     * @return the playback state
     */
    fun target(disassembled: Boolean, hasRecord: Boolean, disabled: Boolean, animationSpeed: Float,
               stalled: Boolean, pauseModeWithRedstone: Boolean): PlaybackState = when {
        disassembled || !hasRecord -> PlaybackState.STOPPED
        !disabled && (abs(animationSpeed) >= SPEED_THRESHOLD || stalled || pauseModeWithRedstone) -> PlaybackState.PLAYING
        else -> PlaybackState.PAUSED
    }
}