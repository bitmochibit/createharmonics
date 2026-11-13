package me.mochibit.createharmonics.content.kinetics.recordPlayer

import me.mochibit.createharmonics.foundation.extension.remapTo
import net.minecraft.nbt.CompoundTag

/**
 * Record player settings
 *
 * @constructor Creates a new RecordPlayerSettings
 * @property mode
 * @property redstonePower the redstone power
 */
class RecordPlayerSettings(val mode: PlaybackMode, val redstonePower: Int) {
    val isPowered get() = redstonePower > 0
    val isFullyPowered get() = redstonePower == MAX_POWER

    val volume: Float
        get() = if (isPowered) redstonePower.toFloat().remapTo(1f, 15f, 0.1f, 1f) else 1f

    /**
     * Radius
     *
     * @param maxRange the max range
     * @return the float
     */
    fun radius(maxRange: Int): Float =
        if (isPowered) redstonePower.remapTo(1, 15, 4, maxRange).toFloat() else 16f

    /**
     * Companion
     * Companion object — holds factory methods and constants for the enclosing class.
     *
     * @constructor Creates a new Companion
     */
    companion object {
        const val MAX_POWER = 15

        /**
         * Returns
         *
         * @param tag
         * @return the record player settings
         */
        fun from(tag: CompoundTag) = RecordPlayerSettings(
            PlaybackMode.fromScrollValue(tag.getInt(RecordPlayerNbtKeys.SCROLL_VALUE)),
            tag.getInt(RecordPlayerNbtKeys.REDSTONE_POWER),
        )
    }
}