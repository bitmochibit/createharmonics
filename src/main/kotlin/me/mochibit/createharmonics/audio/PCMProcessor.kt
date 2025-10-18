package me.mochibit.createharmonics.audio

import kotlin.math.roundToInt

/**
 * Low-level PCM audio processing operations.
 * Separated for clarity and testability.
 */
object PCMProcessor {
    /**
     * Convert byte array to 16-bit PCM samples (little-endian)
     */
    fun ByteArray.toShortArray(): ShortArray {
        val shorts = ShortArray(size / 2)
        for (i in shorts.indices) {
            val offset = i * 2
            shorts[i] = ((this[offset + 1].toInt() and 0xFF) shl 8 or
                    (this[offset].toInt() and 0xFF)).toShort()
        }
        return shorts
    }

    /**
     * Convert 16-bit PCM samples to byte array (little-endian)
     */
    fun ShortArray.toByteArray(): ByteArray {
        val bytes = ByteArray(size * 2)
        for (i in indices) {
            val offset = i * 2
            bytes[offset] = (this[i].toInt() and 0xFF).toByte()
            bytes[offset + 1] = ((this[i].toInt() shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    /**
     * Apply pitch shifting with a constant factor using linear interpolation
     */
    fun ShortArray.pitchShift(factor: Float): ShortArray {
        if (factor == 1.0f) return this

        val outputSize = (size / factor).roundToInt()
        val output = ShortArray(outputSize)

        for (i in output.indices) {
            val inputPos = i * factor
            val index = inputPos.toInt()

            if (index + 1 < size) {
                val fraction = inputPos - index
                val sample1 = this[index].toFloat()
                val sample2 = this[index + 1].toFloat()
                val interpolated = sample1 + fraction * (sample2 - sample1)
                output[i] = interpolated.roundToInt().coerceIn(-32768, 32767).toShort()
            } else if (index < size) {
                output[i] = this[index]
            }
        }

        return output
    }

    /**
     * Apply dynamic pitch shifting using a time-varying pitch function.
     * This is more complex as we need to track both input and output time.
     */
    fun ShortArray.pitchShiftDynamic(pitchFunction: PitchFunction, sampleRate: Int): ShortArray {
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
            if (index < 0 || index + 1 >= size) break

            // Linear interpolation
            val fraction = (inputPosition - index).toFloat()
            val sample1 = this[index].toFloat()
            val sample2 = this[index + 1].toFloat()
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
    fun ShortArray.resample(fromRate: Int, toRate: Int): ShortArray {
        if (fromRate == toRate) return this

        val ratio = fromRate.toFloat() / toRate
        val outputSize = (size / ratio).roundToInt()
        val output = ShortArray(outputSize)

        for (i in output.indices) {
            val inputPos = i * ratio
            val index = inputPos.toInt()

            if (index + 1 < size) {
                val fraction = inputPos - index
                val sample1 = this[index].toFloat()
                val sample2 = this[index + 1].toFloat()
                val interpolated = sample1 + fraction * (sample2 - sample1)
                output[i] = interpolated.roundToInt().coerceIn(-32768, 32767).toShort()
            } else if (index < size) {
                output[i] = this[index]
            }
        }

        return output
    }
}
