package me.mochibit.createharmonics.audio.player

import net.minecraft.world.level.Level
import org.joml.Vector3d

/**
 * Audio spatial context
 *
 */
interface AudioSpatialContext {
    /**
     * Mutate position
     *
     * @param vec
     */
    fun mutatePosition(vec: Vector3d)

    /**
     * Target volume
     *
     * @return the float
     */
    fun targetVolume(): Float

    /**
     * Target pitch
     *
     * @return the float
     */
    fun targetPitch(): Float

    /**
     * Target radius
     *
     * @return the float
     */
    fun targetRadius(): Float

    /**
     * Level
     *
     * @return the level
     */
    fun level(): Level?
}

/**
 * Bind spatial on the receiver [AudioPlayer]
 *
 * @param key
 * @param clock
 * @param context
 */
fun AudioPlayer.bindSpatial(key: Any, clock: PlaytimeClock, context: AudioSpatialContext) {
    if (spatialContextKey === key) return
    sharedTimeline = clock
    spatialContext = context
    spatialContextKey = key
}