package me.mochibit.createharmonics.audio.instance

import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import java.io.InputStream

class SimpleStreamSoundInstance(
    inStream: InputStream,
    streamId: String,
    soundEvent: net.minecraft.sounds.SoundEvent,
    posSupplier: () -> BlockPos,
    volumeSupplier: () -> Float = { 1.0f },
    pitchSupplier: () -> Float = { 1.0f },
    radiusSupplier: () -> Float = { 64f },
    randomSource: RandomSource = RandomSource.create(),
    soundSource: SoundSource = SoundSource.RECORDS,
    looping: Boolean = false,
    delay: Int = 0,
    attenuation: SoundInstance.Attenuation = SoundInstance.Attenuation.LINEAR,
    relative: Boolean = false,
    sampleRate: Int = 44100,
) : StreamingSoundInstance(
        inStream,
        streamId,
        sampleRate,
        soundEvent,
        soundSource,
        randomSource,
        volumeSupplier,
        pitchSupplier,
        posSupplier,
        radiusSupplier,
    ) {
    init {
        this.looping = looping
        this.delay = delay
        this.attenuation = attenuation
        this.relative = relative
    }
}
