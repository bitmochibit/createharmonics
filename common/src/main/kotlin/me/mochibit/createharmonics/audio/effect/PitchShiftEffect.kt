package me.mochibit.createharmonics.audio.effect

import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplier

/**
 * Simple pitch shift effect matching OpenAL/Minecraft behavior.
 * Changes both pitch and playback speed (like changing tape speed).
 *
 * Pitch shift > 1.0 = higher pitch, faster playback
 * Pitch shift < 1.0 = lower pitch, slower playback
 */
class PitchShiftEffect(
    private val pitchShiftProvider: FloatSupplier,
    override val scope: AudioEffect.Scope = AudioEffect.Scope.PERMANENT,
    private var preserveTiming: Boolean = false,
) : AudioEffect {
    private var virtualPosition = 0.0

    private val history = mutableListOf<Short>()
    private var livePos = 0.0

    override fun reset() {
        virtualPosition = 0.0
        history.clear()
        livePos = 0.0
    }

    override fun process(
        samples: ShortArray,
        timeInSeconds: Double,
        sampleRate: Int,
    ): ShortArray {
        val pitchShift = pitchShiftProvider.getValue().coerceIn(0.25f, 4.0f)
        return if (preserveTiming) {
            processTapeSpeed(samples, pitchShift.coerceAtMost(1.0f))
        } else {
            processTapeSpeed(samples, pitchShift)
        }
    }

    private fun processTapeSpeed(
        samples: ShortArray,
        pitchShift: Float,
    ): ShortArray {
        val outputSize = (samples.size / pitchShift).toInt()
        val output = ShortArray(outputSize)
        virtualPosition = 0.0
        for (i in output.indices) {
            val index1 = virtualPosition.toInt()
            val index2 = (index1 + 1).coerceAtMost(samples.size - 1)
            output[i] = lerp(samples[index1], samples[index2], virtualPosition - index1)
            virtualPosition += pitchShift.toDouble()
        }
        return output
    }

    private fun lerp(
        a: Short,
        b: Short,
        t: Double,
    ): Short = (a * (1.0 - t) + b * t).toInt().toShort()

    fun preserveTiming() {
        this.preserveTiming = true
    }

    fun stretchTime() {
        this.preserveTiming = false
    }

    // When preserveTiming, output size == input size, so consumer speed is always 1.0
    override fun getSpeedMultiplier(): Double =
        if (preserveTiming) {
            1.0
        } else {
            pitchShiftProvider.getValue().coerceIn(0.25f, 4.0f).toDouble()
        }
}
