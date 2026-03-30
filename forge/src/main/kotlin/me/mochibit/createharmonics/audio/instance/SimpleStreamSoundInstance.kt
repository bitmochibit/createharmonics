package me.mochibit.createharmonics.audio.instance

import me.mochibit.createharmonics.audio.stream.PcmAudioStream
import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplier
import net.minecraft.client.resources.sounds.Sound
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.AudioStream
import net.minecraft.client.sounds.SoundBufferLibrary
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import java.io.InputStream
import java.util.concurrent.CompletableFuture

class SimpleStreamSoundInstance(
    inStream: InputStream,
    streamId: String,
    soundEvent: net.minecraft.sounds.SoundEvent,
    posSupplier: () -> BlockPos,
    volumeSupplier: FloatSupplier = FloatSupplier { 1.0f },
    pitchSupplier: FloatSupplier = FloatSupplier { 1.0f },
    radiusSupplier: FloatSupplier = FloatSupplier { 64f },
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

    override fun getStream(
        soundBuffers: SoundBufferLibrary,
        sound: Sound,
        looping: Boolean,
    ): CompletableFuture<AudioStream> = CompletableFuture.completedFuture(this.currentAudioStream)
}
