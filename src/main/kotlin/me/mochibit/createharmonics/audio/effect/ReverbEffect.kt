package me.mochibit.createharmonics.audio.effect

import kotlin.math.roundToInt

class ReverbEffect(
    private val roomSize: Float = 0.7f,
    private val damping: Float = 0.4f,
    private val wetMix: Float = 0.5f,
) : AudioEffect {
    companion object {
        private val COMB_TUNINGS = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        private val ALLPASS_TUNINGS = intArrayOf(556, 441, 341, 225)

        private const val SCALE_ROOM = 0.28f
        private const val OFFSET_ROOM = 0.7f
        private const val SCALE_DAMPING = 0.4f
        private const val FIXED_GAIN = 0.015f
        private const val ALLPASS_FEEDBACK = 0.5f
    }

    private val combBuffers = Array(8) { FloatArray(COMB_TUNINGS[it]) }
    private val combIndices = IntArray(8)
    private val combFilterStates = FloatArray(8)

    private val allpassBuffers = Array(4) { FloatArray(ALLPASS_TUNINGS[it]) }
    private val allpassIndices = IntArray(4)

    private var roomScale = 0f
    private var damp1 = 0f
    private var damp2 = 0f

    init {
        updateParameters()
    }

    private fun updateParameters() {
        val safeRoomSize = roomSize.coerceIn(0f, 1f)
        roomScale = (safeRoomSize * SCALE_ROOM + OFFSET_ROOM).coerceIn(0f, 0.98f)

        val safeDamping = damping.coerceIn(0f, 1f)
        damp1 = safeDamping * SCALE_DAMPING
        damp2 = 1.0f - damp1
    }

    override fun process(
        samples: ShortArray,
        timeInSeconds: Double,
        sampleRate: Int,
    ): ShortArray {
        if (wetMix <= 0.0f) return samples

        val output = ShortArray(samples.size)
        val safeWetMix = wetMix.coerceIn(0f, 1f)
        val dryMix = 1.0f - safeWetMix

        for (i in samples.indices) {
            val input = samples[i].toFloat() / 32768.0f
            var combOutput = 0f

            for (c in 0..7) {
                val bufferIndex = combIndices[c]
                val bufferSize = combBuffers[c].size

                if (bufferIndex >= bufferSize) {
                    combIndices[c] = 0
                    continue
                }

                // Read delayed sample
                val delayedSample = combBuffers[c][bufferIndex]

                if (!delayedSample.isFinite()) {
                    combBuffers[c][bufferIndex] = 0f
                    combFilterStates[c] = 0f
                    combIndices[c] = (bufferIndex + 1) % bufferSize
                    continue
                }

                // One-pole lowpass filter (damping)
                val filtered = delayedSample * damp2 + combFilterStates[c] * damp1
                combFilterStates[c] = filtered

                // Write input + feedback to buffer
                val feedback = filtered * roomScale
                combBuffers[c][bufferIndex] = (input + feedback).coerceIn(-2f, 2f)

                combOutput += delayedSample

                combIndices[c] = (bufferIndex + 1) % bufferSize
            }

            var apOutput = combOutput
            for (a in 0..3) {
                val bufferIndex = allpassIndices[a]
                val bufferSize = allpassBuffers[a].size

                if (bufferIndex >= bufferSize) {
                    allpassIndices[a] = 0
                    continue
                }

                val delayed = allpassBuffers[a][bufferIndex]

                if (!apOutput.isFinite() || !delayed.isFinite()) {
                    allpassBuffers[a][bufferIndex] = 0f
                    allpassIndices[a] = (bufferIndex + 1) % bufferSize
                    continue
                }

                // All-pass filter formula
                val apInput = apOutput
                apOutput = -apInput + delayed

                // Store: input + (delayed * feedback)
                allpassBuffers[a][bufferIndex] = (apInput + delayed * ALLPASS_FEEDBACK).coerceIn(-2f, 2f)

                allpassIndices[a] = (bufferIndex + 1) % bufferSize
            }

            if (!apOutput.isFinite()) {
                apOutput = 0f
            }

            // Mix
            val finalSample = input * dryMix + apOutput * safeWetMix * FIXED_GAIN
            val safeFinal = finalSample.coerceIn(-1.0f, 1.0f)
            output[i] = (safeFinal * 32767.0f).roundToInt().coerceIn(-32768, 32767).toShort()
        }

        return output
    }

    override fun reset() {
        combBuffers.forEach { it.fill(0f) }
        combIndices.fill(0)
        combFilterStates.fill(0f)
        allpassBuffers.forEach { it.fill(0f) }
        allpassIndices.fill(0)
        updateParameters()
    }

    override fun getName(): String = "Reverb(room=$roomSize, damp=$damping, wet=$wetMix)"
}
