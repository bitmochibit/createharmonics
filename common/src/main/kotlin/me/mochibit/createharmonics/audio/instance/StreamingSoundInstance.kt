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

/**
 * Streaming sound instance
 *
 * @constructor
 * @property sourceStream the source stream
 * @property sampleRate the sample rate
 * @param audioPlayer the audio player
 * @param soundEvent the sound event
 * @param soundSource the sound source
 * @param randomSource the random source
 */
abstract class StreamingSoundInstance(
    val sourceStream: InputStream,
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

    /**
     * Companion
     * Companion object — holds factory methods and constants for the enclosing class.
     *
     * @constructor Creates a new Companion
     */
    companion object {
        /**
         * Simple factory
         *
         * @param audioPlayer the audio player
         * @param stream
         * @param soundEvent the sound event
         * @param sampleRate the sample rate
         * @param soundSource the sound source
         * @param randomSource the random source
         * @param looping
         * @param attenuation
         * @param delay
         * @param relative
         * @return the streaming sound instance
         */
        fun simpleFactory(
            audioPlayer: AudioPlayer,
            stream: InputStream,
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
