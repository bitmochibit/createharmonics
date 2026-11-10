package me.mochibit.createharmonics.audio.player

import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerNbtKeys
import net.minecraft.nbt.CompoundTag

interface PlaytimeSource {
    val isPlaying: Boolean
    val currentPlaytime: Double
}

class PlaytimeClock() : PlaytimeSource {
    private class Snap(val offset: Double, val playing: Boolean, val anchor: Long, val rate: Double) {
        fun at(now: Long = System.nanoTime()) =
            if (!playing || anchor == -1L) offset else offset + (now - anchor) / 1e9 * rate
    }
    @Volatile private var snap = Snap(0.0, false, -1L, 1.0)

    constructor(nbt: CompoundTag) : this() {
        updateValues(
            if (nbt.contains(RecordPlayerNbtKeys.CLOCK_OFFSET)) nbt.getDouble(RecordPlayerNbtKeys.CLOCK_OFFSET) else 0.0,
            nbt.contains("ClockWasPlaying") && nbt.getBoolean("ClockWasPlaying"),
        )
    }

    var rate: Double
        get() = snap.rate
        @Synchronized set(v) {
            val s = snap; if (v == s.rate) return
            val now = System.nanoTime()
            snap = Snap(s.at(now), s.playing, if (s.playing) now else -1L, v)
        }

    override val currentPlaytime get() = snap.at()
    override val isPlaying get() = snap.playing

    @Synchronized fun tick() {
        val s = snap; if (!s.playing) return
        val now = System.nanoTime(); snap = Snap(s.at(now), true, now, s.rate)
    }
    @Synchronized fun updateValues(offset: Double, isPlaying: Boolean) {
        snap = Snap(offset, isPlaying, if (isPlaying) System.nanoTime() else -1L, snap.rate)
    }
    @Synchronized fun play(from: Double = snap.offset) { snap = Snap(from, true, System.nanoTime(), snap.rate) }
    @Synchronized fun pause() {
        val s = snap; if (!s.playing) return
        snap = Snap(s.at(), false, -1L, s.rate)
    }
    @Synchronized fun stop() { snap = Snap(0.0, false, -1L, snap.rate) }
    fun getValues(): Pair<Double, Boolean> = snap.let { it.at() to it.playing }
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
