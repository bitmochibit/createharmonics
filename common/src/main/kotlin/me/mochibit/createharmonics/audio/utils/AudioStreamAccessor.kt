package me.mochibit.createharmonics.audio.utils

import mixin.SoundEngineAccessor
import mixin.SoundManagerAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.AudioStream
import net.minecraft.sounds.SoundEvent
import net.minecraft.util.RandomSource
import java.util.concurrent.CompletableFuture

fun SoundInstance.getStreamDirectly(looping: Boolean): CompletableFuture<AudioStream> {
    val mc = Minecraft.getInstance()
    val sm = mc.soundManager as SoundManagerAccessor
    val engine = sm.soundEngine
    val engineAccessor = engine as SoundEngineAccessor
    val soundBuffers = engineAccessor.soundBuffers
    return soundBuffers.getStream(this.sound.path, looping)
}

fun SoundEvent.getStreamDirectly(looping: Boolean = false): CompletableFuture<AudioStream> {
    val mc = Minecraft.getInstance()
    val sm = mc.soundManager as SoundManagerAccessor
    val engine = sm.soundEngine as SoundEngineAccessor
    val soundBuffers = engine.soundBuffers

    val weighedSoundEvents =
        mc.soundManager.getSoundEvent(this.location) ?: return CompletableFuture.failedFuture(
            IllegalStateException("No sound event found for ${this.location}"),
        )

    val sound = weighedSoundEvents.getSound(mc.level?.random ?: RandomSource.create())

    if (sound == null) {
        return CompletableFuture.failedFuture(
            IllegalStateException("No sound found in event ${this.location}"),
        )
    }
    return soundBuffers.getStream(sound.path, looping)
}
