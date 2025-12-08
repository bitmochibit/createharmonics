package me.mochibit.createharmonics.audio.instance

import me.mochibit.createharmonics.audio.StreamId
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import java.io.InputStream

class SimpleStreamSoundInstance(
    inStream: InputStream,
    streamId: StreamId,
    soundEvent: net.minecraft.sounds.SoundEvent,
    posSupplier: () -> BlockPos,
    volumeSupplier: () -> Float = { 1.0f },
    pitchSupplier: () -> Float = { 1.0f },
    radiusSupplier: () -> Int = { 16 },
    randomSource: RandomSource = RandomSource.create(),
    soundSource: SoundSource = SoundSource.RECORDS,
    looping: Boolean = false, // TODO: Implement a wrapper to PCMAudioStream which can be looped
    delay: Int = 0,
    attenuation: SoundInstance.Attenuation = SoundInstance.Attenuation.LINEAR,
    relative: Boolean = false,
) : StreamingSoundInstance(
        inStream,
        streamId,
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
