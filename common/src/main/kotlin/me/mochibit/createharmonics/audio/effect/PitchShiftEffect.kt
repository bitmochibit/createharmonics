package me.mochibit.createharmonics.audio.effect

import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplier
import kotlin.math.floor

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
    private var subSampleOffset = 0.0

    private var lastSample: Short = 0

    override fun reset() {
        subSampleOffset = 0.0
        lastSample = 0
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
        if (samples.isEmpty()) return ShortArray(0)
        val pitch = pitchShift.toDouble()

        var pos = subSampleOffset

        // How many output samples fit from [pos, samples.size)?
        val outputSize = ((samples.size - pos) / pitch).toInt().coerceAtLeast(0)
        val output = ShortArray(outputSize)

        for (i in output.indices) {
            val i1 = floor(pos).toInt()
            val frac = pos - i1

            // s1: if i1 < 0 we're interpolating across the chunk boundary
            val s1: Short =
                when {
                    i1 < 0 -> lastSample
                    i1 >= samples.size -> samples.last()
                    else -> samples[i1]
                }
            // s2: i1+1 can equal samples.size on the last output sample — clamp to last
            val s2: Short =
                when {
                    i1 + 1 >= samples.size -> samples.last()
                    i1 + 1 < 0 -> samples[0]
                    else -> samples[i1 + 1]
                }

            output[i] = lerp(s1, s2, frac)
            pos += pitch
        }

        // Persist state for next chunk
        lastSample = samples.last()
        // pos is now in [samples.size - pitch, samples.size + pitch).
        // Subtract samples.size to get the offset into the NEXT chunk's coordinate space.
        subSampleOffset = pos - samples.size

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
