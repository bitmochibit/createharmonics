package me.mochibit.createharmonics.foundation.extension

fun Float.remapTo(
    inMin: Float,
    inMax: Float,
    outMin: Float,
    outMax: Float,
): Float = outMin + (this - inMin) / (inMax - inMin) * (outMax - outMin)

fun Double.remapTo(
    inMin: Double,
    inMax: Double,
    outMin: Double,
    outMax: Double,
): Double = outMin + (this - inMin) / (inMax - inMin) * (outMax - outMin)

fun Int.remapTo(
    inMin: Int,
    inMax: Int,
    outMin: Int,
    outMax: Int,
): Int = outMin + (this - inMin) * (outMax - outMin) / (inMax - inMin)

fun Long.remapTo(
    inMin: Long,
    inMax: Long,
    outMin: Long,
    outMax: Long,
): Long = outMin + (this - inMin) * (outMax - outMin) / (inMax - inMin)

fun Float.lerpTo(
    target: Float,
    t: Float,
): Float = this + (target - this) * t
