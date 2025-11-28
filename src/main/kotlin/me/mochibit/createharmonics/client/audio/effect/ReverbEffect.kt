package me.mochibit.createharmonics.client.audio.effect

import kotlin.math.roundToInt

/**
 * Simple reverb effect using a feedback delay network.
 * Creates an echo/room ambience effect.
 */
class ReverbEffect(
    private val roomSize: Float = 0.5f,  // 0.0 to 1.0
    private val damping: Float = 0.5f,    // 0.0 to 1.0
    private val wetMix: Float = 0.3f      // 0.0 to 1.0 (amount of reverb)
) : AudioEffect {

    // Delay line buffers for creating reverb
    private val delayTimes = intArrayOf(1557, 1617, 1491, 1422, 1277, 1356, 1188, 1116) // Prime numbers for natural sound
    private val delayBuffers = Array(delayTimes.size) { FloatArray(3000) } // Max ~62ms at 48kHz
    private val delayPositions = IntArray(delayTimes.size) { 0 }
    private val filterStates = FloatArray(delayTimes.size) { 0f }

    override fun process(samples: ShortArray, timeInSeconds: Double, sampleRate: Int): ShortArray {
        if (wetMix <= 0.0f) return samples // No reverb

        val output = ShortArray(samples.size)
        val dryMix = 1.0f - wetMix

        for (i in samples.indices) {
            val input = samples[i].toFloat()
            var reverbSample = 0f

            // Process through each delay line
            for (j in delayBuffers.indices) {
                val delayTime = (delayTimes[j] * roomSize).roundToInt().coerceAtLeast(1)
                val buffer = delayBuffers[j]
                val pos = delayPositions[j]

                // Read delayed sample
                val delayedSample = buffer[pos]

                // Apply damping filter (simple low-pass)
                filterStates[j] = filterStates[j] * damping + delayedSample * (1.0f - damping)

                // Write new sample with feedback
                val feedback = 0.7f * roomSize
                buffer[pos] = input + filterStates[j] * feedback

                // Update position
                delayPositions[j] = (pos + 1) % delayTime

                // Accumulate reverb
                reverbSample += filterStates[j]
            }

            // Mix dry and wet signals
            reverbSample /= delayBuffers.size
            val mixed = input * dryMix + reverbSample * wetMix
            output[i] = mixed.roundToInt().coerceIn(-32768, 32767).toShort()
        }

        return output
    }

    override fun reset() {
        delayBuffers.forEach { it.fill(0f) }
        delayPositions.fill(0)
        filterStates.fill(0f)
    }

    override fun getName(): String = "Reverb(room=$roomSize, damp=$damping, wet=$wetMix)"
}
