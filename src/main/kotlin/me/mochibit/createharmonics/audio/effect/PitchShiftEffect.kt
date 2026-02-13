package me.mochibit.createharmonics.audio.effect

import me.mochibit.createharmonics.foundation.math.FloatSupplier

/**
 * Simple pitch shift effect matching OpenAL/Minecraft behavior.
 * Changes both pitch and playback speed (like changing tape speed).
 *
 * Pitch shift > 1.0 = higher pitch, faster playback
 * Pitch shift < 1.0 = lower pitch, slower playback
 */
class PitchShiftEffect(
    private val pitchShiftProvider: FloatSupplier,
) : AudioEffect {
    private var virtualPosition = 0.0

    override fun process(
        samples: ShortArray,
        timeInSeconds: Double,
        sampleRate: Int,
    ): ShortArray {
        val pitchShift = pitchShiftProvider.getValue().coerceIn(0.25f, 4.0f)

        // Calculate output size based on pitch shift
        val outputSize = (samples.size / pitchShift).toInt()
        val output = ShortArray(outputSize)

        virtualPosition = 0.0

        for (i in output.indices) {
            val sourceIndex = virtualPosition
            val index1 = sourceIndex.toInt()
            val index2 = (index1 + 1).coerceAtMost(samples.size - 1)
            val frac = sourceIndex - index1

            // Linear interpolation between samples
            output[i] =
                if (index1 < samples.size) {
                    val sample1 = samples[index1].toFloat()
                    val sample2 = samples[index2].toFloat()
                    (sample1 * (1 - frac) + sample2 * frac).toInt().toShort()
                } else {
                    0
                }

            virtualPosition += pitchShift.toDouble()
        }

        return output
    }

    override fun getSpeedMultiplier(): Double = pitchShiftProvider.getValue().coerceIn(0.25f, 4.0f).toDouble()
}
