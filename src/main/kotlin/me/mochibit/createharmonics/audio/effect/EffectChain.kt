package me.mochibit.createharmonics.audio.effect

/**
 * Manages a chain of audio effects that are applied sequentially.
 * Effects are applied in the order they were added.
 */
class EffectChain(
    private val effects: List<AudioEffect> = emptyList(),
) : AudioEffect {
    constructor(vararg effects: AudioEffect) : this(effects.toList())

    override fun process(
        samples: ShortArray,
        timeInSeconds: Double,
        sampleRate: Int,
    ): ShortArray {
        var result = samples
        for (effect in effects) {
            result = effect.process(result, timeInSeconds, sampleRate)
        }
        return result
    }

    override fun reset() {
        effects.forEach { it.reset() }
    }

    override fun getName(): String = "EffectChain[${effects.joinToString(", ") { it.getName() }}]"

    /**
     * Create a new chain with an additional effect appended.
     */
    fun append(effect: AudioEffect): EffectChain = EffectChain(effects + effect)

    /**
     * Check if the chain is empty (no effects).
     */
    fun isEmpty(): Boolean = effects.isEmpty()

    /**
     * Get the number of effects in the chain.
     */
    fun size(): Int = effects.size

    companion object {
        /**
         * Create an empty effect chain (pass-through).
         */
        fun empty(): EffectChain = EffectChain()
    }
}
