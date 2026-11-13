package me.mochibit.createharmonics.audio.player

import me.mochibit.createharmonics.audio.info.AudioInfo
import me.mochibit.createharmonics.audio.stream.AudioEffectInputStream
import net.minecraft.client.resources.sounds.SoundInstance

/**
 * Player intent
 * Sealed hierarchy — all subtypes are known at compile time, ideal for exhaustive `when`.
 *
 */
sealed interface PlayerIntent {
    /**
     * Play
     * Data class — a value holder; `equals`, `hashCode`, `toString` and `copy` are generated.
     *
     * @constructor Creates a new Play
     * @property initialPosition the initial position
     */
    data class Play(
        val initialPosition: Double,
    ) : PlayerIntent

    /**
     * Pause
     * Singleton object — a single shared instance.
     *
     * @constructor Creates a new Pause
     */
    data object Pause : PlayerIntent

    /**
     * Stops
     * Singleton object — a single shared instance.
     *
     * @constructor Creates a new Stop
     */
    data object Stop : PlayerIntent

    /**
     * Audio hanged
     * Singleton object — a single shared instance.
     *
     * @constructor Creates a new AudioHanged
     */
    data object AudioHanged : PlayerIntent

    /**
     * Audio finished
     * Singleton object — a single shared instance.
     *
     * @constructor Creates a new AudioFinished
     */
    data object AudioFinished : PlayerIntent

    /**
     * Stops
     * Singleton object — a single shared instance.
     *
     * @constructor Creates a new Shutdown
     */
    data object Shutdown : PlayerIntent

    /**
     * Seek
     * Data class — a value holder; `equals`, `hashCode`, `toString` and `copy` are generated.
     *
     * @constructor Creates a new Seek
     * @property position
     */
    data class Seek(
        val position: Double,
    ) : PlayerIntent

    /**
     * Creates a new request
     * Data class — a value holder; `equals`, `hashCode`, `toString` and `copy` are generated.
     *
     * @constructor Creates a new NewRequest
     * @property req
     */
    data class NewRequest(
        val req: AudioRequest,
    ) : PlayerIntent

    /**
     * Stream ready
     * Data class — a value holder; `equals`, `hashCode`, `toString` and `copy` are generated.
     *
     * @constructor Creates a new StreamReady
     * @property stream
     * @property soundInstance the sound instance
     * @property audioInfo the audio info
     * @property atPos the at pos
     * @property streamGeneration the stream generation
     */
    data class StreamReady(
        val stream: AudioEffectInputStream,
        val soundInstance: SoundInstance,
        val audioInfo: AudioInfo,
        val atPos: Double,
        val streamGeneration: Int,
    ) : PlayerIntent

    /**
     * Stream failed
     * Data class — a value holder; `equals`, `hashCode`, `toString` and `copy` are generated.
     *
     * @constructor Creates a new StreamFailed
     * @property shouldDisableSeek the should disable seek
     * @property shouldRetry the should retry
     */
    data class StreamFailed(
        val shouldDisableSeek: Boolean = false,
        val shouldRetry: Boolean = false,
    ) : PlayerIntent
}
