package me.mochibit.createharmonics.audio.instance

import me.mochibit.createharmonics.foundation.extension.asResource
import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplier
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import java.io.InputStream

abstract class StreamingSoundInstance(
    val sourceStream: InputStream,
    val streamId: String,
    override var sampleRate: Int = 44100,
    soundEvent: SoundEvent,
    soundSource: SoundSource = SoundSource.RECORDS,
    randomSource: RandomSource = RandomSource.create(),
    volumeSupplier: FloatSupplier = FloatSupplier { 1.0f },
    pitchSupplier: FloatSupplier = FloatSupplier { 1.0f },
    posSupplier: () -> BlockPos = { BlockPos.ZERO },
    radiusSupplier: FloatSupplier = FloatSupplier { 64f },
) : SuppliedSoundInstance(
        soundEvent,
        soundSource,
        randomSource,
        true,
        posSupplier,
        volumeSupplier,
        pitchSupplier,
        radiusSupplier,
    ),
    SampleRatedInstance {
    override fun getLocation(): ResourceLocation = "streaming_sound_instance".asResource()
}
