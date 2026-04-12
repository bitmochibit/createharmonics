package me.mochibit.createharmonics.audio.effect

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Base interface for audio effects that can be chained together.
 * Each effect processes audio samples and can modify them in any way.
 */
interface AudioEffect {
    interface Scope {
        val id: String
        val pipelineOrder: Int

        data class DefaultScope(
            override val id: String,
            override val pipelineOrder: Int,
        ) : Scope

        companion object Registry {
            private val scopes = ConcurrentHashMap<String, Scope>()
            private val orderCounter = AtomicInteger(0)

            fun register(scope: Scope): Scope {
                require(!scopes.containsKey(scope.id)) {
                    "Scope '${scope.id}' is already registered"
                }
                scopes[scope.id] = scope
                orderCounter.updateAndGet { current -> maxOf(current, scope.pipelineOrder + 1) }
                return scope
            }

            fun get(id: String): Scope? = scopes[id]

            fun all(): List<Scope> = scopes.values.sortedBy { it.pipelineOrder }

            /** Returns a unique pipeline order value, safe for concurrent callers. */
            fun nextOrder(): Int = orderCounter.getAndIncrement()

            val PERMANENT = register(DefaultScope("permanent", 0))
            val INTRINSIC_EFFECT = register(DefaultScope("intrinsic_effect", 10))
            val SOUND_COMPOSITION_MIXER = register(DefaultScope("sound_composition_mixer", 20))
            val MACHINE_CONTROLLED_PITCH = register(DefaultScope("machine_controlled_pitch", 30))
            val EXTERNAL_EFFECT = register(DefaultScope("external_effect", 40))
        }
    }

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

    fun tailLengthSeconds(sampleRate: Int): Double = 0.0

    val scope: Scope

    /**
     * Get a human-readable name for this effect (for debugging/logging).
     */
    fun getName(): String = this::class.simpleName ?: "UnknownEffect"
}
