package me.mochibit.createharmonics.audio.comp

import me.mochibit.createharmonics.audio.instance.SimpleStreamSoundInstance
import me.mochibit.createharmonics.audio.instance.SimpleTickableSoundInstance
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource

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
    )

    private val soundInstances: MutableList<SoundInstance> = mutableListOf()

    fun makeComposition(referenceSoundInstance: SoundInstance) {
        for (soundEvent in soundList) {
            val newSoundInstance =
                when (referenceSoundInstance) {
                    is SimpleStreamSoundInstance -> {
                        SimpleTickableSoundInstance(
                            soundEvent.event,
                            soundEvent.source ?: referenceSoundInstance.source,
                            soundEvent.randomSource ?: RandomSource.create(),
                            soundEvent.looping ?: referenceSoundInstance.isLooping,
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
                            soundEvent.looping ?: referenceSoundInstance.isLooping,
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

            Minecraft.getInstance().soundManager.play(newSoundInstance)
            soundInstances.add(newSoundInstance)
        }
    }

    fun stopComposition() {
        for (soundInstance in soundInstances) {
            Minecraft.getInstance().soundManager.stop(soundInstance)
        }
    }
}
