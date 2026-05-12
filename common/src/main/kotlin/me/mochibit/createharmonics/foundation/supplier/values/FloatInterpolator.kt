package me.mochibit.createharmonics.foundation.supplier.values

import kotlin.math.abs
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class FloatInterpolator(
    initial: Float = 0f,
    interpolationDuration: Duration = 150.milliseconds,
) : FloatSupplier {
    @Volatile
    var interpolationDuration =
        interpolationDuration
        set(value) {
            field = value
            smoothingFactor =
                computeSmoothingFactor(value)
        }

    @Volatile
    private var smoothingFactor =
        computeSmoothingFactor(interpolationDuration)

    @Volatile
    private var current = initial

    @Volatile
    private var target = initial

    override fun getValue(): Float = current

    fun setTarget(value: Float) {
        target = value
    }

    fun snapTo(value: Float) {
        current = value
        target = value
    }

    fun tick() {
        current +=
            (target - current) * smoothingFactor

        if (abs(target - current) < 0.0001f) {
            current = target
        }
    }

    private fun computeSmoothingFactor(
        duration: Duration,
        tps: Float = 20f,
        epsilon: Float = 0.01f,
    ): Float {
        if (duration <= Duration.ZERO) {
            return 1f
        }

        val ticks =
            duration.inWholeMilliseconds / 1000f * tps

        return 1f - epsilon.pow(1f / ticks)
    }
}
