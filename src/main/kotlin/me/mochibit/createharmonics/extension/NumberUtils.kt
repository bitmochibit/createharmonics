package me.mochibit.createharmonics.extension

fun Float.remapTo(inMin: Float, inMax: Float, outMin: Float, outMax: Float): Float {
    return outMin + (this - inMin) / (inMax - inMin) * (outMax - outMin)
}

fun Double.remapTo(inMin: Double, inMax: Double, outMin: Double, outMax: Double): Double {
    return outMin + (this - inMin) / (inMax - inMin) * (outMax - outMin)
}

fun Int.remapTo(inMin: Int, inMax: Int, outMin: Int, outMax: Int): Int {
    return outMin + (this - inMin) * (outMax - outMin) / (inMax - inMin)
}

fun Long.remapTo(inMin: Long, inMax: Long, outMin: Long, outMax: Long): Long {
    return outMin + (this - inMin) * (outMax - outMin) / (inMax - inMin)
}