package me.mochibit.createharmonics.audio.effect

/**
 * Manages a chain of audio effects that are applied sequentially.
 * Effects are applied in the order they were added.
 */
class EffectChain(
    @Volatile private var effects: List<AudioEffect> = emptyList(),
) : AudioEffect {
    constructor(vararg effects: AudioEffect) : this(effects.toList())

    override fun process(
        samples: ShortArray,
        timeInSeconds: Double,
        sampleRate: Int,
    ): ShortArray {
        val currentEffects = effects
        var result = samples
        for (effect in currentEffects) {
            result = effect.process(result, timeInSeconds, sampleRate)
        }
        return result
    }

    override fun reset() {
        effects.forEach { it.reset() }
    }

    override fun getName(): String = "EffectChain[${effects.joinToString(", ") { it.getName() }}]"

    @Synchronized
    fun addEffect(effect: AudioEffect) {
        effects = effects + effect
    }

    @Synchronized
    fun removeEffect(effect: AudioEffect) {
        effects = effects - effect
    }

    @Synchronized
    fun removeEffectAt(index: Int) {
        effects = effects.filterIndexed { i, _ -> i != index }
    }

    @Synchronized
    fun setEffects(newEffects: List<AudioEffect>) {
        effects = newEffects.toList()
    }

    @Synchronized
    fun clear() {
        effects = emptyList()
    }

    /**
     * Check if the chain is empty (no effects).
     */
    fun isEmpty(): Boolean = effects.isEmpty()

    /**
     * Get the number of effects in the chain.
     */
    fun size(): Int = effects.size

    fun getEffects(): List<AudioEffect> = effects.toList()

    companion object {
        /**
         * Create an empty effect chain (pass-through).
         */
        fun empty(): EffectChain = EffectChain()
    }
}
