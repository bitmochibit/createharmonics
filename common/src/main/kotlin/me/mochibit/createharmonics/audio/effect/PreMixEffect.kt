package me.mochibit.createharmonics.audio.effect

interface PreMixEffect {
    fun process(samples: ShortArray, sampleCount: Int, sampleRate: Int)
    fun reset() {}
}