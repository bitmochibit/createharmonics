package me.mochibit.createharmonics.foundation.math

import me.mochibit.createharmonics.extension.lerpTo

class FloatSupplierInterpolated(
    private val valueSupplier: () -> Float,
    private val interpolationDurationMs: Long = 100,
) {
    private var lastValue = 0f
    private var targetValue = 0f
    private var lastUpdateTime = 0L

    private val isInitialized: Boolean
        get() = lastUpdateTime != 0L

    fun getValue(): Float {
        val currentTime = System.currentTimeMillis()

        if (!isInitialized) {
            return initialize(currentTime)
        }

        valueSupplier().takeIf { it != targetValue }?.let { newValue ->
            lastValue = getCurrentInterpolatedValue(currentTime)
            targetValue = newValue
            lastUpdateTime = currentTime
        }

        return getCurrentInterpolatedValue(currentTime)
    }

    private fun getCurrentInterpolatedValue(currentTime: Long): Float {
        val elapsed = currentTime - lastUpdateTime
        val t = (elapsed.toFloat() / interpolationDurationMs).coerceIn(0f, 1f)
        return lastValue.lerpTo(targetValue, t)
    }

    private fun initialize(currentTime: Long): Float {
        val initialValue = valueSupplier()
        lastValue = initialValue
        targetValue = initialValue
        lastUpdateTime = currentTime
        return initialValue
    }
}
