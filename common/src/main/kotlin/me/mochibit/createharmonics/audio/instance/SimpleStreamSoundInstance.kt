package me.mochibit.createharmonics.audio.instance

import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplier
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.core.Position
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import org.joml.Vector3d
import java.io.InputStream

class SimpleStreamSoundInstance(
    inStream: InputStream,
    streamId: String,
    soundEvent: SoundEvent,
    posMutator: (vec: Vector3d) -> Unit,
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
        posMutator,
        soundSource,
        randomSource,
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

    // Stream is now accessed by mixins
}
