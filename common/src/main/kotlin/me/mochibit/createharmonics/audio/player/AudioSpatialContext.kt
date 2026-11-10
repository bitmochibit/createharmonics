package me.mochibit.createharmonics.audio.player

import net.minecraft.world.level.Level
import org.joml.Vector3d

interface AudioSpatialContext {
    fun mutatePosition(vec: Vector3d)

    fun targetVolume(): Float

    fun targetPitch(): Float

    fun targetRadius(): Float

    fun level(): Level?
}

fun AudioPlayer.bindSpatial(key: Any, clock: PlaytimeClock, context: AudioSpatialContext) {
    if (spatialContextKey === key) return
    sharedTimeline = clock
    spatialContext = context
    spatialContextKey = key
}