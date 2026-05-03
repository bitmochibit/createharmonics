package me.mochibit.createharmonics.audio.instance

import me.mochibit.createharmonics.compat.ModCompats
import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplier
import mixin.SoundEngineAccessor
import mixin.SoundManagerAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.Sound
import net.minecraft.client.sounds.SoundManager
import net.minecraft.client.sounds.WeighedSoundEvents
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.util.valueproviders.ConstantFloat
import net.minecraft.world.level.Level
import org.joml.Vector3d

abstract class SuppliedSoundInstance(
    soundEvent: SoundEvent,
    soundSource: SoundSource,
    randomSoundInstance: RandomSource,
    private val streamSound: Boolean,
    val posMutator: (vec: Vector3d) -> Unit,
    val volumeSupplier: FloatSupplier,
    val pitchSupplier: FloatSupplier,
    val radiusSupplier: FloatSupplier,
) : AbstractTickableSoundInstance(soundEvent, soundSource, randomSoundInstance) {
    protected var currentRadius = radiusSupplier.getValue()
    protected var currentPitch = pitchSupplier.getValue()
    protected var currentVolume = volumeSupplier.getValue()
    protected var currentPosition = Vector3d()
    private var resolvedSound: Sound? = null

    private val mc = Minecraft.getInstance()
    private val sm = mc.soundManager as SoundManagerAccessor
    protected val engine = sm.soundEngine as SoundEngineAccessor

    private val currentClientLevel: Level? = mc.level

    override fun tick() {
        if (this.isStopped) return

        try {
            currentPitch = pitchSupplier.getValue()
            currentVolume = volumeSupplier.getValue()
            posMutator(currentPosition)
        } catch (e: Exception) {
            return
        }

        if (currentClientLevel != null) {
            ModCompats.sableCompat?.projectOutOfSubLevel(currentClientLevel, currentPosition)
        }

        this.x = currentPosition.x
        this.y = currentPosition.y
        this.z = currentPosition.z

        this.volume = currentVolume
        this.pitch = currentPitch

        try {
            val newRadius = radiusSupplier.getValue()

            currentRadius = newRadius
            engine.instanceToChannel[this]?.execute { channel ->
                channel.linearAttenuation(this.currentRadius)
            }
        } catch (e: Exception) {
            // If supplier throws, don't crash the audio system
        }
    }

    override fun resolve(pHandler: SoundManager): WeighedSoundEvents? {
        val weighedSoundEvents = super.resolve(pHandler)

        // For non-streaming sounds, cache the resolved sound and modify its attenuation
        if (!streamSound && weighedSoundEvents != null) {
            resolvedSound = weighedSoundEvents.getSound(this.random)
        }

        return weighedSoundEvents
    }

    override fun getSound(): Sound {
        // For streaming sounds, create custom Sound object
        if (streamSound) {
            return Sound(
                this.location,
                ConstantFloat.of(1.0f),
                ConstantFloat.of(1.0f),
                1,
                Sound.Type.SOUND_EVENT,
                true,
                false,
                currentRadius.toInt(),
            )
        }

        // For non-streaming sounds, use the resolved sound from sounds.json
        // but create a new Sound with our custom attenuation distance
        val baseSound = resolvedSound ?: this.sound

        return Sound(
            baseSound.location,
            baseSound.volume,
            baseSound.pitch,
            baseSound.weight,
            baseSound.type,
            baseSound.shouldStream(),
            baseSound.shouldPreload(),
            currentRadius.toInt(),
        )
    }
}
