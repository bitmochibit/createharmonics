package me.mochibit.createharmonics.audio.instance

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.Sound
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.SoundManager
import net.minecraft.client.sounds.WeighedSoundEvents
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource

class SimpleMovingSoundInstance(
    soundEvent: SoundEvent,
    soundSource: SoundSource,
    volume: Float,
    pitch: Float,
    randomSource: RandomSource,
    looping: Boolean,
    delay: Int,
    attenuation: SoundInstance.Attenuation,
    relative: Boolean,
    private val posSupplier: () -> BlockPos = { BlockPos.ZERO },
) : AbstractTickableSoundInstance(soundEvent, soundSource, randomSource) {
    init {
        this.volume = volume
        this.pitch = pitch
        this.looping = looping
        this.delay = delay
        this.attenuation = attenuation
        this.relative = relative
    }

    private var currentPosition: BlockPos = posSupplier()

    override fun tick() {
        currentPosition = posSupplier()
    }

    override fun getX(): Double = super.getX()

    override fun getY(): Double = super.getY()

    override fun getZ(): Double = super.getZ()
}
