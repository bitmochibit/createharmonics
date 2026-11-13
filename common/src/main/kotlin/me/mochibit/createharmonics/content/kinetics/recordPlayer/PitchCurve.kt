package me.mochibit.createharmonics.content.kinetics.recordPlayer

import me.mochibit.createharmonics.config.ModConfigs
import kotlin.math.abs

/**
 * Pitch curve
 *
 * @constructor Creates a new PitchCurve
 * @property minAt the min at
 * @property unityStart the unity start
 * @property unityEnd the unity end
 * @property maxAt the max at
 */
class PitchCurve(
    private val minAt: Float, private val unityStart: Float,
    private val unityEnd: Float, private val maxAt: Float,
) {
    /**
     * Evaluate
     *
     * @param input
     * @param minPitch the min pitch
     * @param maxPitch the max pitch
     * @return the float
     */
    fun evaluate(input: Float, minPitch: Float = ModConfigs.server.minPitch.get().toFloat(), maxPitch: Float = ModConfigs.server.maxPitch.get().toFloat()): Float {
        val x = abs(input)
        return when {
            x < unityStart -> minPitch + (1f - minPitch) * ((x - minAt) / (unityStart - minAt)).coerceIn(0f, 1f)
            x > unityEnd -> 1f + (maxPitch - 1f) * ((x - unityEnd) / (maxAt - unityEnd)).coerceIn(0f, 1f)
            else -> 1f
        }
    }

    /**
     * Companion
     * Companion object — holds factory methods and constants for the enclosing class.
     *
     * @constructor Creates a new Companion
     */
    companion object {
        const val UNITY_RPM = 128f
        val RPM = PitchCurve(16f, UNITY_RPM, UNITY_RPM, 256f)
        val CONTROLLED_CONTRAPTION = PitchCurve(0f, 300f, 900f, 1200f)
        val VEHICLE_CONTRAPTION = PitchCurve(0f, 300f, 780f, 1200f)      
    }
}