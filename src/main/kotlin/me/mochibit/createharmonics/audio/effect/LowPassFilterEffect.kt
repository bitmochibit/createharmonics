package me.mochibit.createharmonics.audio.effect

import kotlin.math.roundToInt

/**
 * Low-pass filter effect.
 * Removes high frequencies, creating a muffled/underwater sound.
 */
class LowPassFilterEffect(
    private val cutoffFrequency: Float = 1000f, // Hz
    private val resonance: Float = 0.7f         // 0.0 to 1.0
) : AudioEffect {

    private var previousOutput = 0f
    private var previousInput = 0f

    override fun process(samples: ShortArray, timeInSeconds: Double, sampleRate: Int): ShortArray {
        val output = ShortArray(samples.size)

        // Calculate filter coefficient based on cutoff frequency
        val rc = 1.0f / (2.0f * Math.PI.toFloat() * cutoffFrequency)
        val dt = 1.0f / sampleRate
        val alpha = dt / (rc + dt)

        for (i in samples.indices) {
            val input = samples[i].toFloat()

            // Simple first-order low-pass filter
            previousOutput = previousOutput + alpha * (input - previousOutput)

            // Add resonance (feedback)
            val resonant = previousOutput + resonance * (previousOutput - previousInput)
            previousInput = input

            output[i] = resonant.roundToInt().coerceIn(-32768, 32767).toShort()
        }

        return output
    }

    override fun reset() {
        previousOutput = 0f
        previousInput = 0f
    }

    override fun getName(): String = "LowPass(${cutoffFrequency.toInt()}Hz)"
}
