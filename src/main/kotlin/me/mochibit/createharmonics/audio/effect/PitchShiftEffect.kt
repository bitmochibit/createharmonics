package me.mochibit.createharmonics.audio.effect

import me.mochibit.createharmonics.audio.pcm.PCMProcessor
import me.mochibit.createharmonics.audio.pcm.PitchFunction

/**
 * Pitch shifting effect using a time-varying pitch function.
 */
class PitchShiftEffect(
    private val pitchFunction: PitchFunction,
    private val minPitch: Float = 0.5f,
    private val maxPitch: Float = 2.0f
) : AudioEffect {

    constructor(constantPitch: Float, minPitch: Float = 0.5f, maxPitch: Float = 2.0f)
        : this(PitchFunction.constant(constantPitch), minPitch, maxPitch)

    override fun process(samples: ShortArray, timeInSeconds: Double, sampleRate: Int): ShortArray {
        val pitch = pitchFunction.getPitchAt(timeInSeconds).coerceIn(minPitch, maxPitch)

        // If pitch is 1.0, no processing needed
        if (pitch == 1.0f) return samples

        return PCMProcessor.pitchShift(samples, pitch)
    }

    override fun getName(): String = "PitchShift"
}

