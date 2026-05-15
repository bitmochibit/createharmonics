package me.mochibit.createharmonics.audio.player

import net.minecraft.nbt.CompoundTag

class PlaytimeClock() {
    private var offset: Double = 0.0
    private var _isPlaying: Boolean = false
    private var lastTickNano: Long = -1L

    val currentPlaytime: Double get() {
        if (!_isPlaying || lastTickNano == -1L) return offset
        return offset + (System.nanoTime() - lastTickNano) / 1_000_000_000.0
    }

    constructor(nbt: CompoundTag) : this() {
        val offset = if (nbt.contains("ClockOffset")) nbt.getDouble("ClockOffset") else 0.0
        val playing = if (nbt.contains("ClockWasPlaying")) nbt.getBoolean("ClockWasPlaying") else false
        updateValues(offset, playing)
    }

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

    fun getValues(): Pair<Double, Boolean> = currentPlaytime to _isPlaying

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
    putDouble("ClockOffset", clock.currentPlaytime)
    putBoolean("ClockWasPlaying", clock.isPlaying)
    putLong("ClockSerializedAt", System.currentTimeMillis())
}

fun CompoundTag.updateClock(clock: PlaytimeClock) {
    val offset = getDouble("ClockOffset")
    val playing = getBoolean("ClockWasPlaying")
    val serializedAt = if (contains("ClockSerializedAt")) getLong("ClockSerializedAt") else -1L
    val transitSec =
        if (playing && serializedAt != -1L) {
            ((System.currentTimeMillis() - serializedAt) / 1000.0).coerceIn(0.0, 5.0)
        } else {
            0.0
        }
    clock.updateValues(offset + transitSec, playing)
}
