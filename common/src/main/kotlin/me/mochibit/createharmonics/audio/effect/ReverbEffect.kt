package me.mochibit.createharmonics.audio.effect

import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplier
import kotlin.math.log10
import kotlin.math.roundToInt

class ReverbEffect(
    private val roomSizeSupplier: FloatSupplier,
    private val dampingSupplier: FloatSupplier,
    private val wetMixSupplier: FloatSupplier,
    override val scope: AudioEffect.Scope,
) : AudioEffect {
    companion object {
        private val COMB_TUNINGS = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        private val ALLPASS_TUNINGS = intArrayOf(556, 441, 341, 225)

        private const val SCALE_ROOM = 0.28f
        private const val OFFSET_ROOM = 0.7f
        private const val SCALE_DAMPING = 0.65f
        private const val ALLPASS_FEEDBACK = 0.5f
        private const val BASE_SAMPLE_RATE = 44100
    }

    private var currentSampleRate = -1

    private lateinit var combBuffers: Array<FloatArray>
    private lateinit var combIndices: IntArray
    private lateinit var combFilterStates: FloatArray

    private lateinit var allpassBuffers: Array<FloatArray>
    private lateinit var allpassIndices: IntArray

    private var roomScale = 0f
    private var damp1 = 0f
    private var damp2 = 0f

    private fun scaleTunings(
        base: IntArray,
        sampleRate: Int,
    ): IntArray = IntArray(base.size) { ((base[it] * sampleRate) / BASE_SAMPLE_RATE.toFloat()).roundToInt().coerceAtLeast(1) }

    private fun initBuffers(sampleRate: Int) {
        val scaledComb = scaleTunings(COMB_TUNINGS, sampleRate)
        val scaledAllpass = scaleTunings(ALLPASS_TUNINGS, sampleRate)

        combBuffers = Array(8) { FloatArray(scaledComb[it]) }
        combIndices = IntArray(8)
        combFilterStates = FloatArray(8)

        allpassBuffers = Array(4) { FloatArray(scaledAllpass[it]) }
        allpassIndices = IntArray(4)

        currentSampleRate = sampleRate
    }

    private fun updateParameters() {
        val safeRoomSize = roomSizeSupplier.getValue().coerceIn(0f, 1f)
        roomScale = (safeRoomSize * SCALE_ROOM + OFFSET_ROOM).coerceIn(0f, 0.98f)

        val safeDamping = dampingSupplier.getValue().coerceIn(0f, 1f)
        val curvedDamping = safeDamping * safeDamping
        damp1 = curvedDamping * SCALE_DAMPING
        damp2 = 1.0f - damp1
    }

    override fun process(
        samples: ShortArray,
        timeInSeconds: Double,
        sampleRate: Int,
    ): ShortArray {
        if (sampleRate != currentSampleRate) {
            initBuffers(sampleRate)
        }

        updateParameters()

        val wetMix = wetMixSupplier.getValue().coerceIn(0f, 1f)
        if (wetMix <= 0.0f) return samples

        val output = ShortArray(samples.size)
        val dryMix = 1.0f - wetMix

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

                val delayedSample = combBuffers[c][bufferIndex]

                if (!delayedSample.isFinite()) {
                    combBuffers[c][bufferIndex] = 0f
                    combFilterStates[c] = 0f
                    combIndices[c] = (bufferIndex + 1) % bufferSize
                    continue
                }

                val filtered = delayedSample * damp2 + combFilterStates[c] * damp1
                combFilterStates[c] = filtered

                val feedback = filtered * roomScale
                combBuffers[c][bufferIndex] = (input + feedback).coerceIn(-2f, 2f)

                combOutput += delayedSample
                combIndices[c] = (bufferIndex + 1) % bufferSize
            }

            combOutput /= 8f

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

                val apInput = apOutput
                apOutput = -apInput + delayed
                allpassBuffers[a][bufferIndex] = (apInput + delayed * ALLPASS_FEEDBACK).coerceIn(-2f, 2f)
                allpassIndices[a] = (bufferIndex + 1) % bufferSize
            }

            if (!apOutput.isFinite()) {
                apOutput = 0f
            }

            val finalSample = (input * dryMix + apOutput * wetMix).coerceIn(-1.0f, 1.0f)
            output[i] = (finalSample * 32767.0f).roundToInt().coerceIn(-32768, 32767).toShort()
        }

        return output
    }

    override fun reset() {
        if (currentSampleRate > 0) {
            initBuffers(currentSampleRate)
        }
        updateParameters()
    }

    override fun tailLengthSeconds(sampleRate: Int): Double {
        val longestComb = (COMB_TUNINGS.max() * sampleRate / BASE_SAMPLE_RATE.toFloat()).toDouble()
        val safeRoomSize = roomSizeSupplier.getValue().coerceIn(0f, 1f)
        val scale = (safeRoomSize * SCALE_ROOM + OFFSET_ROOM).coerceIn(0.001f, 0.999f)
        return (
            (longestComb / sampleRate) *
                (-60.0 / (20.0 * log10(scale.toDouble())))
        ).coerceIn(0.0, 10.0)
    }

    override fun getName(): String =
        "Reverb(room=${roomSizeSupplier.getValue()}, damp=${dampingSupplier.getValue()}, wet=${wetMixSupplier.getValue()})"
}
