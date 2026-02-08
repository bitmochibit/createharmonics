package me.mochibit.createharmonics.audio.effect

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Low-pass filter effect using a biquad filter.
 * Removes high frequencies, creating a muffled/underwater sound.
 */
class LowPassFilterEffect(
    cutoffFrequency: Float = 1000f,
    resonance: Float = 0.7f, // 0.0 to ~10.0, with 0.707 being "flat"
) : AudioEffect {
    // Mutable parameters that can be updated dynamically
    private var cutoffFrequency: Float = cutoffFrequency
    private var resonance: Float = resonance
    private var coefficientsDirty = true // Flag to recalculate coefficients when parameters change

    private var x1 = 0f // input delayed by 1 sample
    private var x2 = 0f // input delayed by 2 samples
    private var y1 = 0f // output delayed by 1 sample
    private var y2 = 0f // output delayed by 2 samples

    // Filter coefficients (calculated once per process call)
    private var b0 = 0f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f

    // Denormalization constant to prevent floating point issues
    private val denormalConstant = 1e-25f

    /**
     * Update the cutoff frequency dynamically.
     * @param frequency The new cutoff frequency in Hz
     */
    fun setCutoffFrequency(frequency: Float) {
        if (cutoffFrequency != frequency) {
            cutoffFrequency = frequency
            coefficientsDirty = true
        }
    }

    /**
     * Update the resonance (Q factor) dynamically.
     * @param q The new resonance value (0.0 to ~10.0, with 0.707 being "flat")
     */
    fun setResonance(q: Float) {
        if (resonance != q) {
            resonance = q
            coefficientsDirty = true
        }
    }

    /**
     * Update both cutoff frequency and resonance at once.
     */
    fun updateParameters(
        frequency: Float,
        q: Float,
    ) {
        var changed = false
        if (cutoffFrequency != frequency) {
            cutoffFrequency = frequency
            changed = true
        }
        if (resonance != q) {
            resonance = q
            changed = true
        }
        if (changed) {
            coefficientsDirty = true
        }
    }

    /**
     * Get the current cutoff frequency.
     */
    fun getCutoffFrequency(): Float = cutoffFrequency

    /**
     * Get the current resonance.
     */
    fun getResonance(): Float = resonance

    override fun process(
        samples: ShortArray,
        timeInSeconds: Double,
        sampleRate: Int,
    ): ShortArray {
        // Only recalculate coefficients if parameters changed
        if (coefficientsDirty) {
            calculateCoefficients(sampleRate)
            coefficientsDirty = false
        }

        // Calculate makeup gain to compensate for resonance peaks
        // Peak gain of a low-pass biquad at cutoff is approximately Q
        // Use a more aggressive compensation formula
        val makeupGain = 1.0f / (1.0f + (resonance - 0.707f).coerceAtLeast(0f) * 1.2f)

        // Additional pre-attenuation for very high Q to prevent internal filter clipping
        val preGain =
            if (resonance > 2.0f) {
                0.7f / (resonance / 2.0f)
            } else {
                1.0f
            }

        val output = ShortArray(samples.size)

        for (i in samples.indices) {
            val input = samples[i].toFloat() * preGain

            // Biquad filter difference equation
            var y = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2

            // Denormalization: prevent very small numbers from degrading performance
            y += denormalConstant
            y -= denormalConstant

            // Soft clip the filter output to prevent state explosion
            y = softClipInternal(y)

            // Update delay lines
            x2 = x1
            x1 = input
            y2 = y1
            y1 = y

            // Apply makeup gain and final soft clip
            val scaled = y * makeupGain
            val softClipped = softClipOutput(scaled)
            output[i] = softClipped.roundToInt().coerceIn(-32768, 32767).toShort()
        }

        return output
    }

    /**
     * Soft clipping for internal filter state to prevent runaway resonance.
     */
    private fun softClipInternal(x: Float): Float {
        val threshold = 40000f
        val absX = abs(x)

        return if (absX > threshold) {
            val sign = if (x > 0) 1f else -1f
            sign * (threshold + (absX - threshold) / (1 + (absX - threshold) / 10000f))
        } else {
            x
        }
    }

    /**
     * Soft clipping function for final output.
     * Gradually compresses signal as it approaches the limits.
     */
    private fun softClipOutput(x: Float): Float {
        val threshold = 26000f // Start soft clipping earlier
        val headroom = 6768f // Remaining headroom for soft compression

        return when {
            x > threshold -> threshold + (x - threshold) / (1 + ((x - threshold) / headroom))
            x < -threshold -> -threshold + (x + threshold) / (1 + ((-x - threshold) / headroom))
            else -> x
        }
    }

    private fun calculateCoefficients(sampleRate: Int) {
        // Clamp resonance to prevent instability
        val q = resonance.coerceIn(0.1f, 20.0f)

        val omega = 2.0f * Math.PI.toFloat() * cutoffFrequency / sampleRate
        val cosOmega = cos(omega.toDouble()).toFloat()
        val alpha = kotlin.math.sin(omega.toDouble()).toFloat() / (2.0f * q)

        val b0Temp = (1.0f - cosOmega) / 2.0f
        val b1Temp = 1.0f - cosOmega
        val b2Temp = (1.0f - cosOmega) / 2.0f
        val a0 = 1.0f + alpha
        val a1Temp = -2.0f * cosOmega
        val a2Temp = 1.0f - alpha

        // Normalize coefficients by a0
        b0 = b0Temp / a0
        b1 = b1Temp / a0
        b2 = b2Temp / a0
        a1 = a1Temp / a0
        a2 = a2Temp / a0
    }

    override fun reset() {
        x1 = 0f
        x2 = 0f
        y1 = 0f
        y2 = 0f
    }

    override fun getName(): String = "LowPass(${cutoffFrequency.toInt()}Hz, Q=${String.format("%.1f", resonance)})"
}
