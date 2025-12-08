package me.mochibit.createharmonics.audio.instance

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.Sound
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
    protected var currentPosition = posSupplier()
    protected var currentVolume = volumeSupplier()
    protected var currentPitch = pitchSupplier()
    protected var currentRadius = radiusSupplier()

    override fun tick() {
        currentPosition = posSupplier()
        currentVolume = volumeSupplier()
        currentPitch = pitchSupplier()
        currentRadius = radiusSupplier()
    }

    override fun getSound(): Sound =
        Sound(
            this.location.toString(),
            ConstantFloat.of(currentVolume),
            ConstantFloat.of(currentPitch),
            1,
            Sound.Type.SOUND_EVENT,
            streamSound,
            !streamSound,
            currentRadius,
        )

    override fun getVolume(): Float = currentVolume

    override fun getPitch(): Float = currentPitch

    override fun getX(): Double = currentPosition.x.toDouble()

    override fun getY(): Double = currentPosition.y.toDouble()

    override fun getZ(): Double = currentPosition.z.toDouble()
}
