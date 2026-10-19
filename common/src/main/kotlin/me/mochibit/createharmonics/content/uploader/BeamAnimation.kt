package me.mochibit.createharmonics.content.uploader

import kotlin.math.max

typealias Easing = (Float) -> Float


//todo abstract this stuff

object Easings {
    val LINEAR: Easing = { it }
    val EASE_IN: Easing = { x -> x * x * x }
    val EASE_OUT: Easing = { x -> val k = 1f - x; 1f - k * k * k }
    val EASE_OUT_QUAD: Easing = { x -> val k = 1f - x; 1f - k * k }
    val SMOOTH: Easing = { x -> x * x * (3f - 2f * x) }
}

/** Eased 0..1 progress of a segment that begins at [start] and lasts [duration] ticks. */
private fun track(time: Float, start: Float, duration: Float, easing: Easing): Float =
    easing(((time - start) / duration).coerceIn(0f, 1f))


object BeamTiming {
    const val BLOCKS_PER_TICK = 8f
    const val MIN_HOLD_TICKS = 60f

    fun travelTicks(length: Float) = (length / BLOCKS_PER_TICK).coerceAtLeast(1f)
    fun extendTicks(length: Float) = travelTicks(length)
    fun retractTicks(length: Float, descending: Boolean) =
        travelTicks(length)
}

class BeamFrame(
    val descending: Boolean,
    val length: Float,
    val head: Float,
    val tail: Float,
)

class BeamAnimator {
    private var startTick = -1L
    private var retractTick = -1L
    private var descending = false

    val active get() = startTick >= 0

    fun start(descending: Boolean, now: Long) {
        this.descending = descending
        startTick = now
        retractTick = -1L
    }

    fun tick(now: Long, length: Float, keepAlive: Boolean) {
        if (!active) return
        if (retractTick < 0) {
            val hold = max(BeamTiming.MIN_HOLD_TICKS, BeamTiming.extendTicks(length))
            if (!keepAlive && now - startTick >= hold) retractTick = now
        } else if (now - retractTick >= BeamTiming.retractTicks(length, descending)) {
            startTick = -1L
            retractTick = -1L
        }
    }

    fun frame(now: Float, length: Float): BeamFrame? {
        if (!active) return null
        val travel = BeamTiming.travelTicks(length)
        val t = now - startTick
        val r = now - retractTick

        val retracting = retractTick >= 0


        return BeamFrame(
            descending = descending,
            length = length,
            head = track(t, 0f, travel, Easings.LINEAR),
            tail = if (!retracting) 0f else track(r, 0f, travel, Easings.LINEAR),
        )
    }
}