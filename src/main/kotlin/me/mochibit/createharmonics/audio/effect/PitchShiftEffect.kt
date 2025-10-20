package me.mochibit.createharmonics.audio.effect

import me.mochibit.createharmonics.audio.pcm.PCMProcessor
import me.mochibit.createharmonics.audio.pcm.PitchFunction

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

        // Use dynamic pitch shifting for smooth, continuous pitch changes
        // This evaluates the pitch function for each sample, not just once per chunk
        val result = PCMProcessor.pitchShiftDynamic(samples, boundedPitchFunction, sampleRate)

        return result
    }

    override fun getName(): String = "PitchShift"
}
