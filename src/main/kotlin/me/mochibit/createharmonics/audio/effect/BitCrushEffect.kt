package me.mochibit.createharmonics.audio.effect

import kotlin.math.roundToInt

/**
 * Bit crush effect that degrades audio quality.
 * Simulates low-quality audio by reducing bit depth and sample rate.
 *
 * @param quality Audio quality from 0.0 (lowest quality/most crushed) to 1.0 (full quality/no effect)
 */
class BitCrushEffect(
    private val quality: Float = 1.0f,
) : AudioEffect {
    private var holdSample: Short = 0
    private var sampleCounter = 0

    override fun process(
        samples: ShortArray,
        timeInSeconds: Double,
        sampleRate: Int,
    ): ShortArray {
        if (quality >= 1.0f) return samples // No processing needed

        val qualityClamped = quality.coerceIn(0.0f, 1.0f)

        // Calculate bit depth reduction (16 bits to ~4 bits at quality=0)
        val bitDepth = (4 + (12 * qualityClamped)).toInt().coerceIn(1, 16)
        val levels = (1 shl bitDepth) - 1 // Number of quantization levels

        // Calculate sample rate reduction (downsample factor: 1x to 16x)
        val downsampleFactor = (1 + (15 * (1.0f - qualityClamped))).roundToInt().coerceIn(1, 16)

        val output = ShortArray(samples.size)

        for (i in samples.indices) {
            // Sample rate reduction (sample and hold)
            if (sampleCounter % downsampleFactor == 0) {
                val input = samples[i].toInt()

                // Bit depth reduction (quantization)
                // Convert from [-32768, 32767] to [0, levels]
                val normalized = ((input + 32768) * levels) / 65535

                // Quantize and convert back to [-32768, 32767]
                val quantized = (normalized * 65535) / levels - 32768

                holdSample = quantized.coerceIn(-32768, 32767).toShort()
            }

            output[i] = holdSample
            sampleCounter++
        }

        return output
    }

    override fun reset() {
        holdSample = 0
        sampleCounter = 0
    }

    override fun getName(): String = "BitCrush(quality=${String.format("%.2f", quality)})"
}
