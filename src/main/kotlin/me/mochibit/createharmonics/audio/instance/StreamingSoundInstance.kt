package me.mochibit.createharmonics.audio.instance

import me.mochibit.createharmonics.asResource
import me.mochibit.createharmonics.audio.PcmAudioStream
import me.mochibit.createharmonics.registry.ModSoundsRegistry
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.Sound
import net.minecraft.client.sounds.AudioStream
import net.minecraft.client.sounds.SoundBufferLibrary
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import java.io.InputStream
import java.util.concurrent.CompletableFuture

abstract class StreamingSoundInstance(
    val sourceStream: InputStream,
    val streamId: String,
    soundSource: SoundSource = SoundSource.RECORDS,
    randomSource: RandomSource = RandomSource.create(),
    volumeSupplier: () -> Float = { 1.0f },
    pitchSupplier: () -> Float = { 1.0f },
    posSupplier: () -> BlockPos = { BlockPos.ZERO },
    radiusSupplier: () -> Int = { 16 },
) : SuppliedSoundInstance(
        ModSoundsRegistry.SLIDING_STONE.get(),
        soundSource,
        randomSource,
        true,
        posSupplier,
        volumeSupplier,
        pitchSupplier,
        radiusSupplier,
    ) {
    override fun getLocation(): ResourceLocation = "streaming_sound_instance".asResource()

    override fun getStream(
        soundBuffers: SoundBufferLibrary,
        sound: Sound,
        looping: Boolean,
    ): CompletableFuture<AudioStream?> =
        CompletableFuture.completedFuture(
            PcmAudioStream(this.sourceStream),
        )
}
