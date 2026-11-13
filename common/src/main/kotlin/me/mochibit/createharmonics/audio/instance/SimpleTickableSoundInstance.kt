package me.mochibit.createharmonics.audio.instance

import me.mochibit.createharmonics.audio.player.AudioPlayer
import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplier
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import org.joml.Vector3d

/**
 * Simple tickable sound instance
 *
 * @constructor
 * @param audioPlayer the audio player
 * @param soundEvent the sound event
 * @param soundSource the sound source
 * @param randomSource the random source
 * @param looping
 * @param delay
 * @param attenuation
 * @param relative
 * @param needStream the need stream
 */
class SimpleTickableSoundInstance(
    audioPlayer: AudioPlayer,
    soundEvent: SoundEvent,
    soundSource: SoundSource,
    randomSource: RandomSource,
    looping: Boolean,
    delay: Int,
    attenuation: SoundInstance.Attenuation,
    relative: Boolean,
    needStream: Boolean,
) : AudioPlayerSoundInstance(
        audioPlayer,
        soundEvent,
        soundSource,
        randomSource,
        needStream,
    ) {
    init {
        this.looping = looping
        this.delay = delay
        this.attenuation = attenuation
        this.relative = relative
    }
}
