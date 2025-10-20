package me.mochibit.createharmonics.audio.effect

import me.mochibit.createharmonics.audio.pcm.PCMProcessor
import me.mochibit.createharmonics.audio.pcm.PitchFunction

/**
 * Pitch shifting effect using a time-varying pitch function.
 * Uses dynamic pitch shifting to handle smooth, continuous pitch changes within chunks.
 */
class PitchShiftEffect(
    private val pitchFunction: PitchFunction,
    private val minPitch: Float = 0.5f,
    private val maxPitch: Float = 2.0f
) : AudioEffect {

    constructor(constantPitch: Float, minPitch: Float = 0.5f, maxPitch: Float = 2.0f)
        : this(PitchFunction.constant(constantPitch), minPitch, maxPitch)

    override fun process(samples: ShortArray, timeInSeconds: Double, sampleRate: Int): ShortArray {
        // Create a wrapper function that applies pitch limits and time offset
        val boundedPitchFunction = PitchFunction { time ->
            pitchFunction.getPitchAt(timeInSeconds + time).coerceIn(minPitch, maxPitch)
        }

        // Use dynamic pitch shifting for smooth, continuous pitch changes
        // This evaluates the pitch function for each sample, not just once per chunk
        return PCMProcessor.pitchShiftDynamic(samples, boundedPitchFunction, sampleRate)
    }

    override fun getName(): String = "PitchShift"
}
