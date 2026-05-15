package me.mochibit.createharmonics.foundation.supplier.values

import kotlin.math.abs
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class FloatInterpolator(
    initial: Float = 0f,
    interpolationDuration: Duration = 150.milliseconds,
) : FloatSupplier {
    companion object {
        private const val TICKS_PER_SECOND = 20f
    }

    @Volatile
    var interpolationDuration = interpolationDuration
        set(value) {
            if (value == field) return
            field = value
            smoothingFactor = computeSmoothingFactor(value)
        }

    @Volatile
    private var smoothingFactor = computeSmoothingFactor(interpolationDuration)

    @Volatile
    private var current = initial

    @Volatile
    private var target = initial

    override fun getValue(): Float = current

    fun setTarget(value: Float) {
        target = value
    }

    @Synchronized
    fun snapTo(value: Float) {
        current = value
        target = value
    }

    @Synchronized
    fun tick() {
        val t = target
        var c = current

        c += (t - c) * smoothingFactor

        if (abs(t - c) < abs(t) * 0.001f + 1e-5f) {
            c = t
        }

        current = c
    }

    private fun computeSmoothingFactor(
        duration: Duration,
        epsilon: Float = 0.01f,
    ): Float {
        if (duration <= Duration.ZERO) return 1f
        val ticks = duration.inWholeMilliseconds / 1000f * TICKS_PER_SECOND
        return 1f - epsilon.pow(1f / ticks)
    }
}
