package me.mochibit.createharmonics.audio.pcm

import me.mochibit.createharmonics.audio.pcm.PitchFunction
import kotlin.math.roundToInt

/**
 * Low-level PCM audio processing operations for pitch shifting.
 */
object PCMProcessor {
    /**
     * Apply pitch shifting with a constant factor using linear interpolation
     */
    fun pitchShift(samples: ShortArray, factor: Float): ShortArray {
        if (factor == 1.0f) return samples

        val outputSize = (samples.size / factor).roundToInt()
        val output = ShortArray(outputSize)

        for (i in output.indices) {
            val inputPos = i * factor
            val index = inputPos.toInt()

            if (index + 1 < samples.size) {
                val fraction = inputPos - index
                val sample1 = samples[index].toFloat()
                val sample2 = samples[index + 1].toFloat()
                val interpolated = sample1 + fraction * (sample2 - sample1)
                output[i] = interpolated.roundToInt().coerceIn(-32768, 32767).toShort()
            } else if (index < samples.size) {
                output[i] = samples[index]
            }
        }

        return output
    }

    /**
     * Apply dynamic pitch shifting using a time-varying pitch function.
     * This is more complex as we need to track both input and output time.
     */
    fun pitchShiftDynamic(samples: ShortArray, pitchFunction: PitchFunction, sampleRate: Int): ShortArray {
        val result = mutableListOf<Short>()
        var inputPosition = 0.0  // Current position in the input audio (in samples)
        var outputTime = 0.0     // Current time in the output audio (in seconds)
        val sampleDuration = 1.0 / sampleRate

        while (true) {
            // Get current pitch, clamped to valid range (0.1 to 10.0)
            val currentPitch = pitchFunction.getPitchAt(outputTime).toDouble()
                .coerceIn(0.1, 10.0)

            // Get the integer index and fractional part
            val index = inputPosition.toInt()

            // Check bounds
            if (index < 0 || index + 1 >= samples.size) break

            // Linear interpolation
            val fraction = (inputPosition - index).toFloat()
            val sample1 = samples[index].toFloat()
            val sample2 = samples[index + 1].toFloat()
            val interpolated = sample1 + fraction * (sample2 - sample1)
            result.add(interpolated.roundToInt().coerceIn(-32768, 32767).toShort())

            // Advance input position based on current pitch
            // Higher pitch = move faster through input = shorter output
            inputPosition += currentPitch
            outputTime += sampleDuration
        }

        return result.toShortArray()
    }

    /**
     * Resample audio from one sample rate to another
     */
    fun resample(samples: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (fromRate == toRate) return samples

        val ratio = fromRate.toFloat() / toRate
        val outputSize = (samples.size / ratio).roundToInt()
        val output = ShortArray(outputSize)

        for (i in output.indices) {
            val inputPos = i * ratio
            val index = inputPos.toInt()

            if (index + 1 < samples.size) {
                val fraction = inputPos - index
                val sample1 = samples[index].toFloat()
                val sample2 = samples[index + 1].toFloat()
                val interpolated = sample1 + fraction * (sample2 - sample1)
                output[i] = interpolated.roundToInt().coerceIn(-32768, 32767).toShort()
            } else if (index < samples.size) {
                output[i] = samples[index]
            }
        }

        return output
    }
}