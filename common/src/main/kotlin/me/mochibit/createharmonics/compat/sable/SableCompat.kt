package me.mochibit.createharmonics.compat.sable

import net.minecraft.world.level.Level
import org.joml.Vector3d

/**
 * Sable compat
 *
 */
internal interface SableCompat {
    /**
     * Convert local sublevel space to global space, mutating [currentPosition]
     *
     * @param level
     * @param currentPosition the current position
     */
    fun projectOutOfSubLevel(
        level: Level,
        currentPosition: Vector3d,
    )

    /**
     * Returns `true` if in plot grid
     *
     * @param level
     * @param pos
     * @return `true` if the condition holds, `false` otherwise
     */
    fun isInPlotGrid(
        level: Level,
        pos: Vector3d,
    ): Boolean
}
