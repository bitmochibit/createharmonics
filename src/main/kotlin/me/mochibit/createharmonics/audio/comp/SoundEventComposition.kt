package me.mochibit.createharmonics.audio.comp

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import me.mochibit.createharmonics.audio.instance.SimpleStreamSoundInstance
import me.mochibit.createharmonics.audio.instance.SimpleTickableSoundInstance
import me.mochibit.createharmonics.coroutine.MinecraftClientDispatcher
import me.mochibit.createharmonics.coroutine.launchModCoroutine
import me.mochibit.createharmonics.coroutine.launchRepeating
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import java.time.Duration
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class PitchSupplierInterpolated(
    val pitchSupplier: () -> Float,
    private val interpolationDurationMs: Long = 100, // Time to interpolate between values
) {
    private var startTime = 0L
    private var lastPitchValue = 0f
    private var targetPitchValue = 0f
    private var lastUpdateTime = 0L

    fun getPitch(): Float {
        val currentTime = System.currentTimeMillis()

        // Initialize on first call
        if (startTime == 0L) {
            startTime = currentTime
            lastUpdateTime = currentTime
            lastPitchValue = pitchSupplier()
            targetPitchValue = lastPitchValue
            return lastPitchValue
        }

        // Get new target value from supplier
        val newPitchVal = pitchSupplier()

        // If target changed, start new interpolation
        if (newPitchVal != targetPitchValue) {
            lastPitchValue = getCurrentInterpolatedValue(currentTime)
            targetPitchValue = newPitchVal
            lastUpdateTime = currentTime
        }

        return getCurrentInterpolatedValue(currentTime)
    }

    private fun getCurrentInterpolatedValue(currentTime: Long): Float {
        val elapsed = currentTime - lastUpdateTime

        if (elapsed >= interpolationDurationMs) {
            return targetPitchValue
        }

        // Linear interpolation
        val t = elapsed.toFloat() / interpolationDurationMs.toFloat()
        return lastPitchValue + (targetPitchValue - lastPitchValue) * t
    }
}

class SoundEventComposition(
    val soundList: List<SoundEventDef> = listOf(),
) {
    data class SoundEventDef(
        val event: SoundEvent,
        val source: SoundSource? = null,
        val randomSource: RandomSource? = null,
        val looping: Boolean? = null,
        val delay: Int? = null,
        val attenuation: SoundInstance.Attenuation? = null,
        val relative: Boolean? = null,
        var needStream: Boolean = false,
        var posSupplier: (() -> BlockPos)? = null,
        var pitchSupplier: (() -> Float)? = null,
        var volumeSupplier: (() -> Float)? = null,
        var radiusSupplier: (() -> Int)? = null,
        var probabilitySupplier: (() -> Float)? = null,
    )

    private val soundInstances: MutableList<SoundInstance> = mutableListOf()
    private val probabilityJobs: MutableList<Job> = mutableListOf()

    fun makeComposition(referenceSoundInstance: SoundInstance) {
        for (soundEvent in soundList) {
            val isLooping = soundEvent.looping ?: false
            val hasProbabilitySupplier = soundEvent.probabilitySupplier != null

            // If sound has probabilitySupplier and is NOT looping, use coroutine-based probability system
            if (hasProbabilitySupplier && !isLooping) {
                val job =
                    launchRepeating(context = MinecraftClientDispatcher, Duration.ZERO, 1.seconds) {
                        val probability = soundEvent.probabilitySupplier?.invoke() ?: 0f
                        val randomValue = Random.nextFloat()

                        if (randomValue <= probability) {
                            // Create and play the sound instance
                            val newSoundInstance = createSoundInstance(soundEvent, referenceSoundInstance, false)
                            Minecraft.getInstance().soundManager.play(newSoundInstance)
                        }
                    }
                probabilityJobs.add(job)
            } else {
                // Play normally for looping sounds or sounds without probabilitySupplier
                val newSoundInstance = createSoundInstance(soundEvent, referenceSoundInstance, isLooping)
                Minecraft.getInstance().soundManager.play(newSoundInstance)
                soundInstances.add(newSoundInstance)
            }
        }
    }

    private fun createSoundInstance(
        soundEvent: SoundEventDef,
        referenceSoundInstance: SoundInstance,
        isLooping: Boolean,
    ): SoundInstance =
        when (referenceSoundInstance) {
            is SimpleStreamSoundInstance -> {
                SimpleTickableSoundInstance(
                    soundEvent.event,
                    soundEvent.source ?: referenceSoundInstance.source,
                    soundEvent.randomSource ?: RandomSource.create(),
                    isLooping,
                    soundEvent.delay ?: referenceSoundInstance.delay,
                    soundEvent.attenuation ?: referenceSoundInstance.attenuation,
                    soundEvent.relative ?: referenceSoundInstance.isRelative,
                    soundEvent.needStream,
                    soundEvent.volumeSupplier ?: referenceSoundInstance.volumeSupplier,
                    soundEvent.pitchSupplier ?: referenceSoundInstance.pitchSupplier,
                    soundEvent.posSupplier ?: referenceSoundInstance.posSupplier,
                    soundEvent.radiusSupplier ?: referenceSoundInstance.radiusSupplier,
                )
            }

            else -> {
                SimpleTickableSoundInstance(
                    soundEvent.event,
                    soundEvent.source ?: referenceSoundInstance.source,
                    soundEvent.randomSource ?: RandomSource.create(),
                    isLooping,
                    soundEvent.delay ?: referenceSoundInstance.delay,
                    soundEvent.attenuation ?: referenceSoundInstance.attenuation,
                    soundEvent.relative ?: referenceSoundInstance.isRelative,
                    soundEvent.needStream,
                    soundEvent.volumeSupplier ?: { referenceSoundInstance.volume },
                    soundEvent.pitchSupplier ?: { referenceSoundInstance.pitch },
                    soundEvent.posSupplier ?: {
                        BlockPos(
                            referenceSoundInstance.x.toInt(),
                            referenceSoundInstance.y.toInt(),
                            referenceSoundInstance.z.toInt(),
                        )
                    },
                    soundEvent.radiusSupplier ?: { 16 },
                )
            }
        }

    fun stopComposition() {
        // Stop all sound instances first
        for (soundInstance in soundInstances) {
            try {
                Minecraft.getInstance().soundManager.stop(soundInstance)
            } catch (e: Exception) {
                // Log but continue to stop other sounds
            }
        }
        soundInstances.clear()

        // Cancel all coroutine jobs
        for (job in probabilityJobs) {
            try {
                job.cancel()
            } catch (e: Exception) {
                // Log but continue to cancel other jobs
            }
        }
        probabilityJobs.clear()
    }
}
