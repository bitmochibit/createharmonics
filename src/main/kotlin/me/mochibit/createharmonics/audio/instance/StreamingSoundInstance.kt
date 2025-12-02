package me.mochibit.createharmonics.audio.instance

import me.mochibit.createharmonics.asResource
import me.mochibit.createharmonics.audio.PcmAudioStream
import net.minecraft.client.resources.sounds.Sound
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.AudioStream
import net.minecraft.client.sounds.SoundBufferLibrary
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import java.io.InputStream
import java.util.concurrent.CompletableFuture

abstract class StreamingSoundInstance(
    val sourceStream: InputStream,
    val streamId: String,
    private val actualSoundSource: SoundSource = SoundSource.RECORDS
) : SoundInstance {
    override fun getLocation(): ResourceLocation {
        return "streaming_sound_instance".asResource()
    }

    override fun getSource(): SoundSource {
        return actualSoundSource
    }

    override fun getStream(
        soundBuffers: SoundBufferLibrary,
        sound: Sound,
        looping: Boolean
    ): CompletableFuture<AudioStream?> {
        return CompletableFuture.completedFuture(
            PcmAudioStream(this.sourceStream)
        )
    }
}