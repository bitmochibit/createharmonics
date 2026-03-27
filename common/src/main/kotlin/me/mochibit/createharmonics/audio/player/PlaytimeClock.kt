package me.mochibit.createharmonics.audio.player

import net.minecraft.nbt.CompoundTag

class PlaytimeClock {
    private var offset: Double = 0.0
    private var _isPlaying: Boolean = false
    private var lastTickNano: Long = -1L

    val currentPlaytime: Double get() = offset
    val isPlaying: Boolean get() = _isPlaying

    fun tick() {
        if (!_isPlaying) return
        val now = System.nanoTime()
        if (lastTickNano != -1L) {
            offset += (now - lastTickNano) / 1_000_000_000.0
        }
        lastTickNano = now
    }

    fun updateValues(
        offset: Double,
        isPlaying: Boolean,
    ) {
        this.offset = offset
        this._isPlaying = isPlaying
        this.lastTickNano = if (isPlaying) System.nanoTime() else -1L
    }

    fun getValues(): Pair<Double, Boolean> = offset to _isPlaying

    fun play(from: Double = offset) {
        offset = from
        lastTickNano = System.nanoTime()
        _isPlaying = true
    }

    fun pause() {
        if (!_isPlaying) return
        _isPlaying = false
        lastTickNano = -1L
    }

    fun stop() {
        offset = 0.0
        _isPlaying = false
        lastTickNano = -1L
    }
}

fun CompoundTag.putClock(clock: PlaytimeClock) {
    val (offset, isPlaying) = clock.getValues()
    putDouble("ClockOffset", offset)
    putBoolean("ClockWasPlaying", isPlaying)
}

fun CompoundTag.updateClock(clock: PlaytimeClock) {
    clock.updateValues(
        offset = getDouble("ClockOffset"),
        isPlaying = getBoolean("ClockWasPlaying"),
    )
}
