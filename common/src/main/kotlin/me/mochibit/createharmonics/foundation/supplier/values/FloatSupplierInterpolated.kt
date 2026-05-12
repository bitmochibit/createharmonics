package me.mochibit.createharmonics.foundation.supplier.values

import me.mochibit.createharmonics.foundation.extension.lerpTo

/**
 * Thread-safe interpolated float supplier that smoothly transitions between values.
 */
class FloatSupplierInterpolated(
    private val valueSupplier: FloatSupplier,
    private val interpolationDurationMs: Long = 100,
) : FloatSupplier {
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

    override fun getValue(): Float {
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
                valueSupplier.getValue()
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
                valueSupplier.getValue()
            } catch (e: Exception) {
                0f // Safe default if initial supplier fails
            }
        lastValue = initialValue
        targetValue = initialValue
        lastUpdateTime = currentTime
        return initialValue
    }

    /**
     * Seeds this supplier with an initial value so the first getValue()
     * interpolates *from* that value rather than jumping from 0.
     * Call this before the supplier is handed to anything that reads it.
     */
    fun seedFrom(value: Float) {
        val now = System.currentTimeMillis()
        lastValue = value
        targetValue = value
        lastUpdateTime = now
        cachedTime = now
        cachedResult = value
    }
}
