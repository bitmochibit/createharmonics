package me.mochibit.createharmonics.audio.player

import net.minecraft.nbt.CompoundTag

interface PlaytimeSource {
    val isPlaying: Boolean
    val currentPlaytime: Double
}

class PlaytimeClock(): PlaytimeSource {
    private var offset: Double = 0.0
    private var _isPlaying: Boolean = false
    private var lastTickNano: Long = -1L

    @Volatile
    var rate: Double = 1.0
        set(value) {
            if (value == field) return
            offset = currentPlaytime
            if (_isPlaying) lastTickNano = System.nanoTime()
            field = value
        }

    override val currentPlaytime: Double
        get() {
            if (!_isPlaying || lastTickNano == -1L) return offset
            return offset + (System.nanoTime() - lastTickNano) / 1e9 * rate
        }

    constructor(nbt: CompoundTag) : this() {
        val offset = if (nbt.contains("ClockOffset")) nbt.getDouble("ClockOffset") else 0.0
        val playing = if (nbt.contains("ClockWasPlaying")) nbt.getBoolean("ClockWasPlaying") else false
        updateValues(offset, playing)
    }

    override val isPlaying: Boolean get() = _isPlaying

    fun tick() {
        if (!_isPlaying) return
        val now = System.nanoTime()
        if (lastTickNano != -1L) offset += (now - lastTickNano) / 1e9 * rate
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
        offset = currentPlaytime
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
}

fun CompoundTag.updateClock(clock: PlaytimeClock) {
    clock.updateValues(
        getDouble("ClockOffset"),
        getBoolean("ClockWasPlaying"),
    )
}
