package me.mochibit.createharmonics.audio.effect

import kotlin.math.roundToInt

/**
 * Volume/gain control effect.
 * Useful for normalizing audio or creating fade effects.
 */
class VolumeEffect(
    private val volumeFunction: (timeInSeconds: Double) -> Float,
    override val scope: AudioEffect.Scope = AudioEffect.Scope.PERMANENT,
) : AudioEffect {
    constructor(constantVolume: Float) : this({ constantVolume })

    override fun process(
        samples: ShortArray,
        timeInSeconds: Double,
        sampleRate: Int,
    ): ShortArray {
        val volume = volumeFunction(timeInSeconds).coerceIn(0.0f, 10.0f)

        // If volume is 1.0, no processing needed
        if (volume == 1.0f) return samples

        return ShortArray(samples.size) { i ->
            (samples[i] * volume).roundToInt().coerceIn(-32768, 32767).toShort()
        }
    }

    override fun getName(): String = "Volume"

    companion object {
        /**
         * Create a fade-in effect.
         */
        fun fadeIn(
            durationSeconds: Double,
            scope: AudioEffect.Scope,
        ): VolumeEffect =
            VolumeEffect({ time ->
                (time / durationSeconds).toFloat().coerceIn(0.0f, 1.0f)
            }, scope)

        /**
         * Create a fade-out effect.
         */
        fun fadeOut(
            startTime: Double,
            durationSeconds: Double,
            scope: AudioEffect.Scope,
        ): VolumeEffect =
            VolumeEffect({ time ->
                if (time < startTime) {
                    1.0f
                } else {
                    1.0f - ((time - startTime) / durationSeconds).toFloat().coerceIn(0.0f, 1.0f)
                }
            }, scope)
    }
}
