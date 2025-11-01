package me.mochibit.createharmonics.audio.effect.pitchShift

import me.mochibit.createharmonics.audio.effect.AudioEffect
import kotlin.math.roundToInt

/**
 * Pitch shifting effect using a time-varying pitch function.
 * Uses dynamic pitch shifting to handle smooth, continuous pitch changes within chunks.
 *
 * NOTE: For real-time pitch control (e.g., jukebox RPM), the pitchFunction should
 * use real-time state (volatile variables) rather than the timeInSeconds parameter.
 */
class PitchShiftEffect(
    private val pitchFunction: PitchFunction,
    private val minPitch: Float = 0.5f,
    private val maxPitch: Float = 2.0f
) : AudioEffect {

    constructor(constantPitch: Float, minPitch: Float = 0.5f, maxPitch: Float = 2.0f)
        : this(PitchFunction.constant(constantPitch), minPitch, maxPitch)

    override fun process(samples: ShortArray, timeInSeconds: Double, sampleRate: Int): ShortArray {
        // Create a wrapper function that applies pitch limits and uses buffer time for smoothing
        val boundedPitchFunction = PitchFunction { time ->
            // The pitchFunction reads currentPitch (volatile) which updates in real-time
            // We pass the time parameter for smooth transitions, but the underlying
            // custom function ignores it and returns the current volatile value
            val pitch = pitchFunction.getPitchAt(time).coerceIn(minPitch, maxPitch)
            pitch
        }

        val result = pitchShiftDynamic(samples, boundedPitchFunction, sampleRate)

        return result
    }

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

    override fun getName(): String = "PitchShift"
}