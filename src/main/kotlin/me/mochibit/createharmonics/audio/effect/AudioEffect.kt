package me.mochibit.createharmonics.audio.effect

/**
 * Base interface for audio effects that can be chained together.
 * Each effect processes audio samples and can modify them in any way.
 */
interface AudioEffect {
    /**
     * Process audio samples at a given time position.
     *
     * @param samples Input audio samples (16-bit PCM)
     * @param timeInSeconds Current time position in the audio stream
     * @param sampleRate Sample rate of the audio
     * @return Processed audio samples
     */
    fun process(
        samples: ShortArray,
        timeInSeconds: Double,
        sampleRate: Int,
    ): ShortArray

    /**
     * Reset the effect's internal state (e.g., for reverb buffers).
     * Called when seeking or restarting playback.
     */
    fun reset() {}

    /**
     * Boolean to check if the effect is currently running at base values, so virtually no effect is applied. This can be used to skip processing for optimization.
     * Or to delay its removal or addition.
     */
    fun isBaseValues(): Boolean = false

    fun getSpeedMultiplier(): Double = 1.0

    /**
     * Get a human-readable name for this effect (for debugging/logging).
     */
    fun getName(): String = this::class.simpleName ?: "UnknownEffect"
}
