package me.mochibit.createharmonics.audio.instance

import me.mochibit.createharmonics.audio.utils.getStreamDirectly
import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplier
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.AudioStream
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import org.joml.Vector3d
import java.util.concurrent.CompletableFuture

class SimpleTickableSoundInstance(
    soundEvent: SoundEvent,
    soundSource: SoundSource,
    randomSource: RandomSource,
    looping: Boolean,
    delay: Int,
    attenuation: SoundInstance.Attenuation,
    relative: Boolean,
    needStream: Boolean,
    posMutator: (vec: Vector3d) -> Unit,
    volumeSupplier: FloatSupplier = FloatSupplier { 1.0f },
    pitchSupplier: FloatSupplier = FloatSupplier { 1.0f },
    radiusSupplier: FloatSupplier = FloatSupplier { 64f },
) : SuppliedSoundInstance(
        soundEvent,
        soundSource,
        randomSource,
        needStream,
        posMutator,
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
}
