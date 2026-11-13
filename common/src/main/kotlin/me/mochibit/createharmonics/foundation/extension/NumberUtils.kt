package me.mochibit.createharmonics.foundation.extension

import kotlin.math.floor

/**
 * Remap to on the receiver [Float]
 *
 * @param inMin the in min
 * @param inMax the in max
 * @param outMin the out min
 * @param outMax the out max
 * @return the float
 */
fun Float.remapTo(
    inMin: Float,
    inMax: Float,
    outMin: Float,
    outMax: Float,
): Float = outMin + (this - inMin) / (inMax - inMin) * (outMax - outMin)

/**
 * Remap to on the receiver [Double]
 *
 * @param inMin the in min
 * @param inMax the in max
 * @param outMin the out min
 * @param outMax the out max
 * @return the double
 */
fun Double.remapTo(
    inMin: Double,
    inMax: Double,
    outMin: Double,
    outMax: Double,
): Double = outMin + (this - inMin) / (inMax - inMin) * (outMax - outMin)

/**
 * Remap to on the receiver [Int]
 *
 * @param inMin the in min
 * @param inMax the in max
 * @param outMin the out min
 * @param outMax the out max
 * @return the int
 */
fun Int.remapTo(
    inMin: Int,
    inMax: Int,
    outMin: Int,
    outMax: Int,
): Int = outMin + (this - inMin) * (outMax - outMin) / (inMax - inMin)

/**
 * Remap to on the receiver [Long]
 *
 * @param inMin the in min
 * @param inMax the in max
 * @param outMin the out min
 * @param outMax the out max
 * @return the long
 */
fun Long.remapTo(
    inMin: Long,
    inMax: Long,
    outMin: Long,
    outMax: Long,
): Long = outMin + (this - inMin) * (outMax - outMin) / (inMax - inMin)

/**
 * Lerp to on the receiver [Float]
 *
 * @param target
 * @param t
 * @return the float
 */
fun Float.lerpTo(
    target: Float,
    t: Float,
): Float = this + (target - this) * t

/**
 * Converts the block coord on the receiver [Double]
 *
 * @return the int
 */
fun Double.toBlockCoord(): Int = floor(this).toInt()
