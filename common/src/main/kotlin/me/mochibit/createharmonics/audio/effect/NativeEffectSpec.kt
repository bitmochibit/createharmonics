package me.mochibit.createharmonics.audio.effect

/**
 * All the specifications for native effects provided by openal
 */
sealed interface NativeEffectSpec {
    data class Pitch(val multiplier: Float) : NativeEffectSpec

    data class LowPass(val cutoffFrequency: Float, val resonance: Float) : NativeEffectSpec

    data class Reverb(
        val roomSize: Float,
        val damping: Float,
        val wetMix: Float,
    ) : NativeEffectSpec
}