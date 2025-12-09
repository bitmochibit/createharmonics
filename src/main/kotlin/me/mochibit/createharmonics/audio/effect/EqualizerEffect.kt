package me.mochibit.createharmonics.audio.effect

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Represents a single band in the parametric equalizer.
 *
 * @param frequency Center frequency in Hz
 * @param quality Q factor (bandwidth control, typically 0.5 to 10.0)
 * @param gain Gain in dB (-20 to +20)
 */
data class EQBand(
    val frequency: Float,
    val quality: Float = 1.0f,
    val gain: Float = 0f,
)

/**
 * Multi-band parametric equalizer effect.
 * Allows independent control of multiple frequency bands.
 *
 * Uses biquad filters for each band with adjustable gain and quality.
 *
 * @param bands List of EQ bands to apply
 */
class EqualizerEffect(
    private var bands: List<EQBand> =
        listOf(
            EQBand(frequency = 200f, quality = 1.0f, gain = 0f),
            EQBand(frequency = 1000f, quality = 1.0f, gain = 0f),
            EQBand(frequency = 6000f, quality = 1.0f, gain = 0f),
        ),
) : AudioEffect {
    // Biquad filter for each band
    private var filters = mutableListOf<BiquadFilter>()

    private var currentSampleRate = 44100

    // Automatic gain compensation to prevent clipping
    private var gainCompensation = 1.0f

    init {
        updateFilters(currentSampleRate)
    }

    /**
     * Update the list of EQ bands and recalculate filters.
     *
     * @param newBands New list of EQ bands
     */
    fun setBands(newBands: List<EQBand>) {
        bands = newBands
        updateFilters(currentSampleRate)
    }

    /**
     * Update a specific band by index.
     *
     * @param index Index of the band to update
     * @param band New band parameters
     */
    fun updateBand(
        index: Int,
        band: EQBand,
    ) {
        if (index in bands.indices) {
            val mutableBands = bands.toMutableList()
            mutableBands[index] = band
            bands = mutableBands
            updateFilters(currentSampleRate)
        }
    }

    override fun process(
        samples: ShortArray,
        timeInSeconds: Double,
        sampleRate: Int,
    ): ShortArray {
        if (currentSampleRate != sampleRate) {
            currentSampleRate = sampleRate
            updateFilters(sampleRate)
        }

        val output = ShortArray(samples.size)

        for (i in samples.indices) {
            var signal = samples[i].toFloat()

            // Process through each band in series (chain filters)
            for (filter in filters) {
                signal = filter.process(signal)
            }

            // Apply automatic gain compensation to prevent clipping
            signal *= gainCompensation

            output[i] = signal.roundToInt().coerceIn(-32768, 32767).toShort()
        }

        return output
    }

    override fun reset() {
        filters.forEach { it.reset() }
    }

    private fun updateFilters(sampleRate: Int) {
        filters.clear()
        for (band in bands) {
            filters.add(createPeakingFilter(band.frequency, band.gain, band.quality, sampleRate))
        }

        // Calculate gain compensation for positive gains to prevent clipping
        val totalPositiveGain = bands.sumOf { if (it.gain > 0) it.gain.toDouble() else 0.0 }.toFloat()
        gainCompensation =
            if (totalPositiveGain > 0) {
                // Apply modest compensation for large positive gains
                1.0f / (1.0f + totalPositiveGain / 30.0f)
            } else {
                1.0f
            }
    }

    private fun createPeakingFilter(
        freq: Float,
        gainDb: Float,
        quality: Float,
        sampleRate: Int,
    ): BiquadFilter {
        val filter = BiquadFilter()
        val a = 10.0.pow((gainDb / 40.0)).toFloat()
        val w0 = 2.0 * Math.PI * freq / sampleRate
        val cosW0 = cos(w0).toFloat()
        val sinW0 = sin(w0).toFloat()
        val alpha = sinW0 / (2.0 * quality) // Use the quality parameter

        val b0 = 1 + alpha * a
        val b1 = -2 * cosW0
        val b2 = 1 - alpha * a
        val a0 = 1 + alpha / a
        val a1 = -2 * cosW0
        val a2 = 1 - alpha / a

        filter.setCoefficients(
            (b0 / a0).toFloat(),
            (b1 / a0).toFloat(),
            (b2 / a0).toFloat(),
            (a1 / a0).toFloat(),
            (a2 / a0).toFloat(),
        )

        return filter
    }

    override fun getName(): String {
        val bandInfo = bands.joinToString(", ") { "${it.frequency.toInt()}Hz:${it.gain.toInt()}dB" }
        return "EQ($bandInfo)"
    }

    /**
     * Biquad filter implementation for parametric EQ.
     */
    private class BiquadFilter {
        private var b0 = 1f
        private var b1 = 0f
        private var b2 = 0f
        private var a1 = 0f
        private var a2 = 0f

        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f

        fun setCoefficients(
            b0: Float,
            b1: Float,
            b2: Float,
            a1: Float,
            a2: Float,
        ) {
            this.b0 = b0
            this.b1 = b1
            this.b2 = b2
            this.a1 = a1
            this.a2 = a2
        }

        fun process(input: Float): Float {
            val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2

            // Update state
            x2 = x1
            x1 = input
            y2 = y1
            y1 = output

            return output
        }

        fun reset() {
            x1 = 0f
            x2 = 0f
            y1 = 0f
            y2 = 0f
        }
    }
}
