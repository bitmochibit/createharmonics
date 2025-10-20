package me.mochibit.createharmonics.audio.pcm

import kotlin.math.sin

/**
 * Functional interface for dynamic pitch calculation.
 * Allows pitch to vary over time during audio playback.
 *
 * @param timeInSeconds The current time position in the audio (in seconds)
 * @return The pitch shift factor at this time (1.0 = normal, >1.0 = higher, <1.0 = lower)
 */
fun interface PitchFunction {
    fun getPitchAt(timeInSeconds: Double): Float

    companion object {
        /**
         * Constant pitch (no variation over time)
         */
        fun constant(pitch: Float): PitchFunction = PitchFunction { pitch }

        /**
         * Linear interpolation between two pitch values
         */
        fun linear(startPitch: Float, endPitch: Float, durationSeconds: Double): PitchFunction =
            PitchFunction { time ->
                val progress = (time / durationSeconds).coerceIn(0.0, 1.0).toFloat()
                startPitch + (endPitch - startPitch) * progress
            }

        /**
         * Oscillating pitch (vibrato effect)
         *
         * @param basePitch The center pitch value
         * @param amplitude The oscillation amplitude (±amplitude). Keep this small (e.g., 0.05 for ±5%)
         * @param frequency The oscillation frequency in Hz
         */
        fun oscillate(basePitch: Float, amplitude: Float, frequency: Double): PitchFunction =
            PitchFunction { time ->
                basePitch + amplitude * sin(2 * Math.PI * frequency * time).toFloat()
            }

        /**
         * Step function - discrete pitch changes at intervals
         */
        fun steps(pitches: List<Float>, stepDuration: Double): PitchFunction =
            PitchFunction { time ->
                val index = (time / stepDuration).toInt().coerceIn(0, pitches.size - 1)
                pitches[index]
            }

        /**
         * Custom function from a lambda
         */
        fun custom(fn: (Double) -> Float): PitchFunction = PitchFunction(fn)
    }
}