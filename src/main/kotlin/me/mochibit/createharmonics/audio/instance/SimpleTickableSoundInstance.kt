package me.mochibit.createharmonics.audio.instance

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource

class SimpleTickableSoundInstance(
    soundEvent: SoundEvent,
    soundSource: SoundSource,
    randomSource: RandomSource,
    looping: Boolean,
    delay: Int,
    attenuation: SoundInstance.Attenuation,
    relative: Boolean,
    needStream: Boolean,
    volumeSupplier: () -> Float = { 1.0f },
    pitchSupplier: () -> Float = { 1.0f },
    posSupplier: () -> BlockPos = { BlockPos.ZERO },
    radiusSupplier: () -> Int = { 16 },
) : SuppliedSoundInstance(
        soundEvent,
        soundSource,
        randomSource,
        needStream,
        posSupplier,
        volumeSupplier,
        pitchSupplier,
        radiusSupplier,
    ) {
    init {
        this.looping = looping
        this.delay = delay
        this.attenuation = attenuation
        this.relative = relative
    }
}
