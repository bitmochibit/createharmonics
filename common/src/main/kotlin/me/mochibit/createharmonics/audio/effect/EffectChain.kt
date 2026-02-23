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
        // Synchronize to ensure we have a consistent view of the effects list
        // This is very fast as we just capture the reference
        val currentEffects = synchronized(this) { effects }
        var result = samples
        for (effect in currentEffects) {
            result = effect.process(result, timeInSeconds, sampleRate)
        }
        return result
    }

    override fun getSpeedMultiplier(): Double {
        // Check if theres a pitch shift effect in the chain, and if so return its multiplier, otherwise return 1.0
        val currentEffects = synchronized(this) { effects }
        for (effect in currentEffects) {
            val multiplier = effect.getSpeedMultiplier()
            if (multiplier != 1.0) {
                return multiplier
            }
        }
        return 1.0
    }

    override fun reset() {
        effects.forEach { it.reset() }
    }

    override fun getName(): String = "EffectChain[${effects.joinToString(", ") { it.getName() }}]"

    /**
     * Normalize an index for insertion (allows 0 to size inclusive).
     * Negative indices count from the end: -1 = size (append), -2 = size-1, etc.
     * Positive indices beyond size wrap around.
     */
    private fun normalizeInsertIndex(
        index: Int,
        size: Int,
    ): Int {
        if (size == 0) return 0
        return when {
            index >= 0 -> {
                index % (size + 1)
            }

            else -> {
                // For negative: -1 = size (end), -2 = size-1, etc.
                val normalized = size + 1 + (index % (size + 1))
                if (normalized <= size) normalized else normalized % (size + 1)
            }
        }
    }

    /**
     * Normalize an index for accessing existing elements (allows 0 to size-1).
     * Negative indices count from the end: -1 = size-1 (last), -2 = size-2, etc.
     * Positive indices beyond size wrap around.
     */
    private fun normalizeAccessIndex(
        index: Int,
        size: Int,
    ): Int {
        if (size == 0) return 0
        return when {
            index >= 0 -> {
                index % size
            }

            else -> {
                // For negative: -1 = size-1 (last), -2 = size-2, etc.
                val normalized = size + (index % size)
                if (normalized >= 0) normalized else normalized + size
            }
        }
    }

    /**
     * Add an effect at the end of the chain.
     */
    @Synchronized
    fun addEffect(effect: AudioEffect) {
        effects = effects + effect
    }

    /**
     * Insert an effect at a specific position in the chain.
     * @param index The position to insert at. Supports:
     *              - Positive: 0 = first, 1 = second, etc.
     *              - Negative: -1 = at end, -2 = before last, etc.
     *              - Out of bounds indices wrap around circularly
     * @param effect The effect to insert.
     */
    @Synchronized
    fun addEffectAt(
        index: Int,
        effect: AudioEffect,
    ) {
        val normalizedIndex = normalizeInsertIndex(index, effects.size)
        effects = effects.toMutableList().apply { add(normalizedIndex, effect) }
    }

    /**
     * Add an effect as the first effect in the chain.
     */
    @Synchronized
    fun addEffectFirst(effect: AudioEffect) {
        effects = listOf(effect) + effects
    }

    /**
     * Add an effect as the last effect in the chain.
     */
    @Synchronized
    fun addEffectLast(effect: AudioEffect) {
        effects = effects + effect
    }

    /**
     * Remove a specific effect instance from the chain.
     */
    @Synchronized
    fun removeEffect(effect: AudioEffect) {
        effects = effects - effect
    }

    /**
     * Remove an effect at a specific index.
     * @param index The position to remove from. Supports:
     *              - Positive: 0 = first, 1 = second, etc.
     *              - Negative: -1 = last, -2 = second-to-last, etc.
     *              - Out of bounds indices wrap around circularly
     */
    @Synchronized
    fun removeEffectAt(
        index: Int,
        removeOnlyIfBased: Boolean = false,
    ) {
        if (effects.isEmpty()) return
        val normalizedIndex = normalizeAccessIndex(index, effects.size)
        val effect = effects[normalizedIndex]
        if (removeOnlyIfBased && !effect.isBaseValues()) return
        effects = effects.filterIndexed { i, _ -> i != normalizedIndex }
    }

    /**
     * Move an effect from one position to another.
     * @param fromIndex The current index of the effect (supports negative/wrapping).
     * @param toIndex The target index for the effect (supports negative/wrapping).
     */
    @Synchronized
    fun moveEffect(
        fromIndex: Int,
        toIndex: Int,
    ) {
        if (effects.isEmpty()) return

        val normalizedFrom = normalizeAccessIndex(fromIndex, effects.size)
        val mutableEffects = effects.toMutableList()
        val effect = mutableEffects.removeAt(normalizedFrom)

        // After removal, size is one less
        val normalizedTo = normalizeInsertIndex(toIndex, mutableEffects.size)
        mutableEffects.add(normalizedTo, effect)
        effects = mutableEffects
    }

    /**
     * Replace an effect at a specific index.
     * @param index The position to replace at (supports negative/wrapping).
     * @param effect The new effect.
     */
    @Synchronized
    fun replaceEffectAt(
        index: Int,
        effect: AudioEffect,
    ) {
        if (effects.isEmpty()) return
        val normalizedIndex = normalizeAccessIndex(index, effects.size)
        effects = effects.toMutableList().apply { this[normalizedIndex] = effect }
    }

    /**
     * Set all effects in the chain, replacing any existing effects.
     */
    @Synchronized
    fun setEffects(newEffects: List<AudioEffect>) {
        effects = newEffects.toList()
    }

    /**
     * Remove all effects from the chain.
     */
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

    /**
     * Get a copy of the current effects list.
     */
    fun getEffects(): List<AudioEffect> = effects.toList()

    /**
     * Get the effect at a specific index (supports negative/wrapping).
     * @param index The position (0 = first, -1 = last, etc.)
     */
    fun getEffectAt(index: Int): AudioEffect? {
        if (effects.isEmpty()) return null
        val normalizedIndex = normalizeAccessIndex(index, effects.size)
        return effects[normalizedIndex]
    }

    /**
     * Find the index of a specific effect instance.
     * @return The index of the effect, or -1 if not found.
     */
    fun indexOf(effect: AudioEffect): Int = effects.indexOf(effect)

    companion object {
        /**
         * Create an empty effect chain (pass-through).
         */
        fun empty(): EffectChain = EffectChain()
    }
}
