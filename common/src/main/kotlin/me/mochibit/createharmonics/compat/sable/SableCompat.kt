package me.mochibit.createharmonics.compat.sable

import net.minecraft.world.level.Level
import org.joml.Vector3d

internal interface SableCompat {
    /**
     * Convert local sublevel space to global space, mutating [currentPosition]
     */
    fun projectOutOfSubLevel(
        level: Level,
        currentPosition: Vector3d,
    )

    fun isInPlotGrid(
        level: Level,
        pos: Vector3d,
    ): Boolean
}
