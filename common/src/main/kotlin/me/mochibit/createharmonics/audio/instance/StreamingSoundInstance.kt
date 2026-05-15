package me.mochibit.createharmonics.audio.instance

import me.mochibit.createharmonics.audio.player.AudioPlayer
import me.mochibit.createharmonics.audio.stream.PcmAudioStream
import me.mochibit.createharmonics.foundation.extension.asResource
import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplier
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.AudioStream
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import org.joml.Vector3d
import java.io.InputStream

abstract class StreamingSoundInstance(
    val sourceStream: InputStream,
    val streamId: String,
    override var sampleRate: Int = 44100,
    audioPlayer: AudioPlayer,
    soundEvent: SoundEvent,
    soundSource: SoundSource = SoundSource.RECORDS,
    randomSource: RandomSource = RandomSource.create(),
) : AudioPlayerSoundInstance(
        audioPlayer,
        soundEvent,
        soundSource,
        randomSource,
        true,
    ),
    SampleRatedInstance {
    override fun getLocation(): ResourceLocation = "streaming_sound_instance".asResource()

    companion object {
        fun simpleFactory(
            audioPlayer: AudioPlayer,
            stream: InputStream,
            streamId: String,
            soundEvent: SoundEvent,
            sampleRate: Int = 44100,
            soundSource: SoundSource = SoundSource.RECORDS,
            randomSource: RandomSource = RandomSource.create(),
            looping: Boolean = false,
            attenuation: SoundInstance.Attenuation = SoundInstance.Attenuation.LINEAR,
            delay: Int = 0,
            relative: Boolean = false,
        ): StreamingSoundInstance =
            SimpleStreamSoundInstance(
                audioPlayer,
                stream,
                streamId,
                soundEvent,
                randomSource,
                soundSource,
                looping,
                delay,
                attenuation,
                relative,
                sampleRate,
            )
    }

    val currentAudioStreamDelegate =
        lazy {
            PcmAudioStream(this.sourceStream, this.sampleRate)
        }
    val currentAudioStream: AudioStream by currentAudioStreamDelegate
}
