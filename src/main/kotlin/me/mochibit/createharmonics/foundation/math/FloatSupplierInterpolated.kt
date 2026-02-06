package me.mochibit.createharmonics.foundation.math

import me.mochibit.createharmonics.extension.lerpTo

/**
 * Thread-safe interpolated float supplier that smoothly transitions between values.
 */
class FloatSupplierInterpolated(
    private val valueSupplier: () -> Float,
    private val interpolationDurationMs: Long = 100,
) {
    @Volatile
    private var lastValue = 0f

    @Volatile
    private var targetValue = 0f

    @Volatile
    private var lastUpdateTime = 0L

    @Volatile
    private var cachedTime = 0L

    @Volatile
    private var cachedResult = 0f

    private val isInitialized: Boolean
        get() = lastUpdateTime != 0L

    fun getValue(): Float {
        // Use cached time if called multiple times in same millisecond
        val currentTime = System.currentTimeMillis()

        // If called multiple times in same millisecond, return cached result
        if (isInitialized && currentTime == cachedTime) {
            return cachedResult
        }

        cachedTime = currentTime

        if (!isInitialized) {
            cachedResult = initialize(currentTime)
            return cachedResult
        }

        // Wrap valueSupplier in try-catch to prevent crashes
        val newValue =
            try {
                valueSupplier()
            } catch (e: Exception) {
                // If supplier fails, keep using current target
                targetValue
            }

        if (newValue != targetValue) {
            lastValue = getCurrentInterpolatedValue(currentTime)
            targetValue = newValue
            lastUpdateTime = currentTime
        }

        cachedResult = getCurrentInterpolatedValue(currentTime)
        return cachedResult
    }

    private fun getCurrentInterpolatedValue(currentTime: Long): Float {
        val elapsed = currentTime - lastUpdateTime
        val t = (elapsed.toFloat() / interpolationDurationMs).coerceIn(0f, 1f)
        return lastValue.lerpTo(targetValue, t)
    }

    private fun initialize(currentTime: Long): Float {
        val initialValue =
            try {
                valueSupplier()
            } catch (e: Exception) {
                0f // Safe default if initial supplier fails
            }
        lastValue = initialValue
        targetValue = initialValue
        lastUpdateTime = currentTime
        return initialValue
    }
}
