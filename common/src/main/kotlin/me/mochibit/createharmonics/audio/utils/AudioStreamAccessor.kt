package me.mochibit.createharmonics.audio.utils

import com.mojang.blaze3d.audio.Channel
import kotlinx.coroutines.future.await
import me.mochibit.createharmonics.audio.instance.StreamingSoundInstance
import me.mochibit.createharmonics.audio.stream.PausableAudioStream
import me.mochibit.createharmonics.foundation.async.withMainContext
import mixin.SoundEngineAccessor
import mixin.SoundManagerAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.AudioStream
import net.minecraft.client.sounds.SoundManager
import net.minecraft.sounds.SoundEvent
import net.minecraft.util.RandomSource
import java.util.concurrent.CompletableFuture

val mcSoundManager: SoundManager by lazy {
    Minecraft.getInstance().soundManager
}

val soundManagerAccessor: SoundManagerAccessor by lazy {
    mcSoundManager as SoundManagerAccessor
}

val soundEngineAccessor: SoundEngineAccessor by lazy {
    val engine = soundManagerAccessor.soundEngine
    engine as SoundEngineAccessor
}

fun SoundInstance.getStreamDirectly(looping: Boolean): CompletableFuture<AudioStream> {
    val soundBuffers = soundEngineAccessor.soundBuffers
    this.resolve(mcSoundManager)

    return soundBuffers.getStream(this.sound.path, looping)
}

fun SoundInstance.pause() {
    if (this is StreamingSoundInstance) {
        if (!currentAudioStreamDelegate.isInitialized()) return
        val currentAudioStream = this.currentAudioStream
        if (currentAudioStream is PausableAudioStream) {
            currentAudioStream.pause()
        }
    }
    val channelHandle = soundEngineAccessor.instanceToChannel[this]
    channelHandle?.execute(Channel::pause)
}

fun SoundInstance.unpause() {
    if (this is StreamingSoundInstance) {
        if (!currentAudioStreamDelegate.isInitialized()) return
        val currentAudioStream = this.currentAudioStream
        if (currentAudioStream is PausableAudioStream) {
            currentAudioStream.resume()
        }
    }
    val channelHandle = soundEngineAccessor.instanceToChannel[this]
    channelHandle?.execute(Channel::unpause)
}

fun SoundEvent.getStreamDirectly(looping: Boolean = false): CompletableFuture<AudioStream> {
    val mc = Minecraft.getInstance()
    val soundBuffers =
        (mc.soundManager as SoundManagerAccessor)
            .let { it.soundEngine as SoundEngineAccessor }
            .soundBuffers

    val weighedSoundEvents =
        mc.soundManager.getSoundEvent(this.location)
            ?: return CompletableFuture.failedFuture(
                IllegalStateException("No sound event found for ${this.location}"),
            )

    val sound =
        weighedSoundEvents.getSound(RandomSource.create())
            ?: return CompletableFuture.failedFuture(
                IllegalStateException("No sound found in event ${this.location}"),
            )

    return soundBuffers.getStream(sound.path, looping)
}
