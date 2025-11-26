package me.mochibit.createharmonics.audio.effect.pitchShift

import kotlin.math.abs
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

        /**
         * Smoothed pitch function that interpolates between changing pitch values.
         * This is perfect for real-time pitch control (like jukebox speed) where the
         * target pitch changes abruptly but you want smooth audio transitions.
         *
         * @param sourcePitchFunction The underlying pitch function (can change instantly)
         * @param transitionTimeSeconds How long to take when transitioning to a new pitch (e.g., 0.1 = 100ms)
         * @return A smoothed version of the pitch function
         */
        fun smoothed(sourcePitchFunction: PitchFunction, transitionTimeSeconds: Double = 0.15): PitchFunction {
            return SmoothedPitchFunction(sourcePitchFunction, transitionTimeSeconds)
        }

        /**
         * Create a smoothed pitch function using real-time wall-clock for transitions.
         * This is better for real-time pitch control (e.g., jukebox RPM changes).
         *
         * @param sourcePitchFunction The underlying pitch function (can change instantly)
         * @param transitionTimeSeconds How long to take when transitioning to a new pitch
         * @return A smoothed version that responds to real-time changes
         */
        fun smoothedRealTime(sourcePitchFunction: PitchFunction, transitionTimeSeconds: Double = 0.15): PitchFunction {
            return RealTimeSmoothedPitchFunction(sourcePitchFunction, transitionTimeSeconds)
        }
    }
}

/**
 * Internal class that implements smooth pitch interpolation.
 * Uses time-based linear interpolation to gradually move from current to target pitch.
 */
private class SmoothedPitchFunction(
    private val sourcePitchFunction: PitchFunction,
    private val transitionTimeSeconds: Double
) : PitchFunction {

    @Volatile
    private var currentPitch: Float = 1.0f

    @Volatile
    private var targetPitch: Float = 1.0f

    @Volatile
    private var transitionStartTime: Double = 0.0

    @Volatile
    private var transitionStartPitch: Float = 1.0f

    @Volatile
    private var isFirstCall = true

    override fun getPitchAt(timeInSeconds: Double): Float {
        // Get the target pitch from the source function
        val newTargetPitch = sourcePitchFunction.getPitchAt(timeInSeconds)

        // Initialize on first call
        if (isFirstCall) {
            currentPitch = newTargetPitch
            targetPitch = newTargetPitch
            transitionStartPitch = newTargetPitch
            transitionStartTime = timeInSeconds
            isFirstCall = false
            return currentPitch
        }

        // Check if target has changed
        if (newTargetPitch != targetPitch) {
            // Start a new transition
            transitionStartPitch = currentPitch
            targetPitch = newTargetPitch
            transitionStartTime = timeInSeconds
        }

        // Calculate progress through the transition (0.0 to 1.0)
        val timeSinceTransitionStart = timeInSeconds - transitionStartTime
        val progress = if (transitionTimeSeconds > 0.0) {
            (timeSinceTransitionStart / transitionTimeSeconds).coerceIn(0.0, 1.0)
        } else {
            1.0 // Instant transition if transitionTime is 0
        }

        // Linear interpolation from start to target
        currentPitch = transitionStartPitch + (targetPitch - transitionStartPitch) * progress.toFloat()

        return currentPitch
    }
}

/**
 * Real-time smoothed pitch function that uses wall-clock time for transitions.
 * This is suitable for real-time pitch control where the system clock is the
 * reference for timing (e.g., jukebox RPM changes).
 */
private class RealTimeSmoothedPitchFunction(
    private val sourcePitchFunction: PitchFunction,
    private val transitionTimeSeconds: Double
) : PitchFunction {

    @Volatile
    private var currentPitch: Float = 1.0f

    @Volatile
    private var targetPitch: Float = 1.0f

    @Volatile
    private var transitionStartTimeMillis: Long = 0L

    @Volatile
    private var transitionStartPitch: Float = 1.0f

    @Volatile
    private var isFirstCall = true


    override fun getPitchAt(timeInSeconds: Double): Float {
        // Get the target pitch from the source function (ignoring timeInSeconds)
        // The source function uses real-time state (volatile variables)
        val newTargetPitch = sourcePitchFunction.getPitchAt(0.0)

        val nowMillis = System.currentTimeMillis()

        // Initialize on first call
        if (isFirstCall) {
            currentPitch = newTargetPitch
            targetPitch = newTargetPitch
            transitionStartPitch = newTargetPitch
            transitionStartTimeMillis = nowMillis
            isFirstCall = false
            return currentPitch
        }

        // Check if target has changed (with small epsilon to avoid float precision issues)
        val epsilon = 0.001f
        if (abs(newTargetPitch - targetPitch) > epsilon) {
            // Start a new transition using wall-clock time
            transitionStartPitch = currentPitch
            targetPitch = newTargetPitch
            transitionStartTimeMillis = nowMillis
        }

        // Calculate progress through the transition (0.0 to 1.0) using wall-clock time
        val timeSinceTransitionStartSeconds = (nowMillis - transitionStartTimeMillis) / 1000.0
        val progress = if (transitionTimeSeconds > 0.0) {
            (timeSinceTransitionStartSeconds / transitionTimeSeconds).coerceIn(0.0, 1.0)
        } else {
            1.0 // Instant transition if transitionTime is 0
        }

        // Linear interpolation from start to target
        currentPitch = transitionStartPitch + (targetPitch - transitionStartPitch) * progress.toFloat()


        return currentPitch
    }
}
