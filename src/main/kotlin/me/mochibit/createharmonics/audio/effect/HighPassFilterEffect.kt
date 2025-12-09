package me.mochibit.createharmonics.audio.effect

import kotlin.math.roundToInt

/**
 * High-pass filter effect.
 * Removes low frequencies, creating a thin/tinny sound.
 */
class HighPassFilterEffect(
    // Hz
    private val cutoffFrequency: Float = 1000f,
    // 0.0 to 1.0
    private val resonance: Float = 0.7f,
) : AudioEffect {
    private var previousOutput = 0f
    private var previousInput = 0f

    override fun process(
        samples: ShortArray,
        timeInSeconds: Double,
        sampleRate: Int,
    ): ShortArray {
        val output = ShortArray(samples.size)

        // Calculate filter coefficient based on cutoff frequency
        val rc = 1.0f / (2.0f * Math.PI.toFloat() * cutoffFrequency)
        val dt = 1.0f / sampleRate
        val alpha = dt / (rc + dt)

        for (i in samples.indices) {
            val input = samples[i].toFloat()

            // High-pass filter: output = input - low_pass
            val lowPass = previousOutput + alpha * (input - previousOutput)
            val highPass = input - lowPass

            // Add resonance (feedback)
            val resonant = highPass + resonance * (highPass - previousInput)
            previousInput = highPass
            previousOutput = lowPass

            output[i] = resonant.roundToInt().coerceIn(-32768, 32767).toShort()
        }

        return output
    }

    override fun reset() {
        previousOutput = 0f
        previousInput = 0f
    }

    override fun getName(): String = "HighPass(${cutoffFrequency.toInt()}Hz)"
}
