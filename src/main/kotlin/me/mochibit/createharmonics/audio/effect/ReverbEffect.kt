package me.mochibit.createharmonics.audio.effect

import kotlin.math.roundToInt

/**
 * Enhanced reverb effect using a feedback delay network with early reflections.
 * Creates a rich room ambience effect with natural decay.
 *
 * @param roomSize 0.0 to 1.0 - larger values create longer reverb tail
 * @param damping 0.0 to 1.0 - higher values create darker/warmer sound
 * @param wetMix 0.0 to 1.0 - amount of reverb in the output
 */
class ReverbEffect(
    private val roomSize: Float = 0.7f,
    private val damping: Float = 0.4f,
    private val wetMix: Float = 0.5f,
) : AudioEffect {
    // Early reflection delays (in samples) - creates initial room response
    private val earlyDelayTimes = intArrayOf(397, 457, 541, 631, 727, 839)
    private val earlyBuffers = Array(earlyDelayTimes.size) { FloatArray(2000) }
    private val earlyPositions = IntArray(earlyDelayTimes.size) { 0 }

    // Late reverb delay lines - longer times for tail (much larger for cathedral effect)
    private val lateDelayTimes = intArrayOf(3557, 3617, 3491, 3422, 3277, 3356, 3188, 3116)
    private val lateBuffers = Array(lateDelayTimes.size) { FloatArray(32000) } // Much larger for cathedral reverb
    private val latePositions = IntArray(lateDelayTimes.size) { 0 }
    private val filterStates = FloatArray(lateDelayTimes.size) { 0f }

    override fun process(
        samples: ShortArray,
        timeInSeconds: Double,
        sampleRate: Int,
    ): ShortArray {
        if (wetMix <= 0.0f) return samples // No reverb

        val output = ShortArray(samples.size)
        val dryMix = 1.0f - wetMix

        for (i in samples.indices) {
            val input = samples[i].toFloat()
            var earlyReflections = 0f
            var lateReverb = 0f

            // Process early reflections (no feedback, shorter delays)
            for (j in earlyBuffers.indices) {
                val delayTime =
                    (earlyDelayTimes[j] * (0.5f + roomSize * 1.5f))
                        .roundToInt()
                        .coerceIn(1, earlyBuffers[j].size - 1)
                val buffer = earlyBuffers[j]
                val pos = earlyPositions[j]

                // Read delayed sample from the buffer
                val delayedSample = buffer[pos]
                earlyReflections += delayedSample * 0.8f

                // Write new input to buffer (overwrite old data)
                buffer[pos] = input

                // Update position (circular buffer)
                earlyPositions[j] = (pos + 1) % delayTime
            }

            // Process late reverb (with feedback for tail)
            for (j in lateBuffers.indices) {
                val delayTime =
                    (lateDelayTimes[j] * (1.0f + roomSize * 4.0f))
                        .roundToInt()
                        .coerceIn(1, lateBuffers[j].size - 1)
                val buffer = lateBuffers[j]
                val pos = latePositions[j]

                // Read delayed sample from the buffer
                val delayedSample = buffer[pos]

                // Apply damping filter (simple low-pass)
                filterStates[j] = filterStates[j] * damping + delayedSample * (1.0f - damping)

                // Write new sample with feedback (mix input with filtered delayed signal)
                val feedback = 0.88f + (roomSize * 0.1f) // Much stronger feedback for long tail
                buffer[pos] = (input * 0.05f) + (filterStates[j] * feedback)

                // Update position (circular buffer)
                latePositions[j] = (pos + 1) % delayTime

                // Accumulate reverb
                lateReverb += filterStates[j]
            }

            // Normalize and combine early + late reverb
            earlyReflections /= earlyBuffers.size
            lateReverb /= lateBuffers.size
            val reverbSample = earlyReflections * 0.5f + lateReverb * 1.5f // Boost late reverb

            // Mix dry and wet signals
            val mixed = input * dryMix + reverbSample * wetMix * 2.0f // Amplify wet signal
            output[i] = mixed.roundToInt().coerceIn(-32768, 32767).toShort()
        }

        return output
    }

    override fun reset() {
        earlyBuffers.forEach { it.fill(0f) }
        earlyPositions.fill(0)
        lateBuffers.forEach { it.fill(0f) }
        latePositions.fill(0)
        filterStates.fill(0f)
    }

    override fun getName(): String = "Reverb(room=$roomSize, damp=$damping, wet=$wetMix)"
}
