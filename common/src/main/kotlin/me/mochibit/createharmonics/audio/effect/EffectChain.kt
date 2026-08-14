package me.mochibit.createharmonics.audio.effect

import kotlin.reflect.KClass

/**
 * Manages a chain of audio effects that are applied sequentially.
 * Effects are applied in the order they were added.
 */
class EffectChain {
    @Volatile private var nativeEffects: Map<KClass<out NativeEffectSpec>, NativeEffectSpec> = emptyMap()
    @Volatile private var preMixEffects: List<PreMixEffect> = emptyList()

    @Synchronized
    fun setNative(spec: NativeEffectSpec) {
        nativeEffects = nativeEffects + (spec::class to spec)
    }

    @Synchronized
    fun clearNative(type: KClass<out NativeEffectSpec>) {
        nativeEffects = nativeEffects - type
    }

    fun snapshotNative(): Collection<NativeEffectSpec> = nativeEffects.values

    @Synchronized
    fun addPreMix(effect: PreMixEffect) {
        preMixEffects = preMixEffects + effect
    }

    @Synchronized
    fun removePreMix(effect: PreMixEffect) {
        preMixEffects = preMixEffects - effect
    }

    fun applyPreMix(samples: ShortArray, sampleCount: Int, sampleRate: Int) {
        val chain = preMixEffects
        for (effect in chain) effect.process(samples, sampleCount, sampleRate)
    }

    @Synchronized
    fun reset() {
        preMixEffects.forEach { it.reset() }
    }
}