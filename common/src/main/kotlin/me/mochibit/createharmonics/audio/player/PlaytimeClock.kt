package me.mochibit.createharmonics.audio.player

import net.minecraft.nbt.CompoundTag
import kotlin.math.abs

class PlaytimeClock {
    private var startedAt: Long = -1L
    private var offset: Double = 0.0
    private var _isPlaying: Boolean = false

    fun updateValues(
        startedAt: Long,
        offset: Double,
        isPlaying: Boolean,
    ) {
        this.startedAt = startedAt
        this.offset = offset
        this._isPlaying = isPlaying
    }

    fun getValues(): Triple<Long, Double, Boolean> = Triple(startedAt, offset, isPlaying)

    val currentPlaytime: Double
        get() =
            if (_isPlaying) {
                offset + (System.nanoTime() - startedAt) / 1_000_000_000.0
            } else {
                offset
            }

    val isPlaying: Boolean get() = _isPlaying

    fun play(from: Double = offset) {
        offset = from
        startedAt = System.nanoTime()
        _isPlaying = true
    }

    fun pause() {
        if (!_isPlaying) return
        offset = currentPlaytime
        _isPlaying = false
    }

    fun stop() {
        offset = 0.0
        _isPlaying = false
    }
}

fun CompoundTag.putClock(clock: PlaytimeClock) {
    val (startedAt, offset, isPlaying) = clock.getValues()
    putLong("ClockPlayTime", startedAt)
    putDouble("ClockOffset", offset)
    putBoolean("ClockWasPlaying", isPlaying)
}

fun CompoundTag.updateClock(clock: PlaytimeClock) {
    val startedAt = this.getLong("ClockPlayTime")
    val offset = this.getDouble("ClockOffset")
    val isPlaying = this.getBoolean("ClockWasPlaying")
    clock.updateValues(startedAt, offset, isPlaying)
}
