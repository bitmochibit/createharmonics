package me.mochibit.createharmonics.audio.instance

import mixin.SoundEngineAccessor
import mixin.SoundManagerAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.Sound
import net.minecraft.client.sounds.SoundManager
import net.minecraft.client.sounds.WeighedSoundEvents
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.util.valueproviders.ConstantFloat

abstract class SuppliedSoundInstance(
    soundEvent: SoundEvent,
    soundSource: SoundSource,
    randomSoundInstance: RandomSource,
    private val streamSound: Boolean,
    val posSupplier: () -> BlockPos,
    val volumeSupplier: () -> Float,
    val pitchSupplier: () -> Float,
    val radiusSupplier: () -> Int,
) : AbstractTickableSoundInstance(soundEvent, soundSource, randomSoundInstance) {
    protected var currentRadius = radiusSupplier()
    protected var currentPitch = pitchSupplier()
    protected var currentVolume = volumeSupplier()
    protected var currentPosition = posSupplier()
    private var resolvedSound: Sound? = null

    private val mc = Minecraft.getInstance()
    private val sm = mc.soundManager as SoundManagerAccessor
    private val engine = sm.soundEngine as SoundEngineAccessor
    private val map = engine.instanceToChannel

    override fun tick() {
        currentPitch = pitchSupplier()
        currentVolume = volumeSupplier()
        currentPosition = posSupplier()

        this.x = currentPosition.x.toDouble()
        this.y = currentPosition.y.toDouble()
        this.z = currentPosition.z.toDouble()

        this.volume = currentVolume
        this.pitch = currentPitch

        val newRadius = radiusSupplier()
        if (newRadius != currentRadius) {
            currentRadius = newRadius
            map[this]?.execute { channel ->
                channel.linearAttenuation(this.currentRadius.toFloat())
            }
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
                this.location.toString(),
                ConstantFloat.of(1.0f),
                ConstantFloat.of(1.0f),
                1,
                Sound.Type.SOUND_EVENT,
                true,
                false,
                currentRadius,
            )
        }

        // For non-streaming sounds, use the resolved sound from sounds.json
        // but create a new Sound with our custom attenuation distance
        val baseSound = resolvedSound ?: this.sound

        return Sound(
            baseSound.location.toString(),
            baseSound.volume,
            baseSound.pitch,
            baseSound.weight,
            baseSound.type,
            baseSound.shouldStream(),
            baseSound.shouldPreload(),
            currentRadius,
        )
    }
}
