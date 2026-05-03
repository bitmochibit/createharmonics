package me.mochibit.createharmonics.mixin;

import me.mochibit.createharmonics.audio.instance.StreamingSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

import java.util.concurrent.CompletableFuture;

@Mixin(StreamingSoundInstance.class)
public abstract class StreamingSoundInstanceMixin implements SoundInstance {
    @Override
    public @NotNull CompletableFuture<AudioStream> getStream(@NotNull SoundBufferLibrary soundBuffers, @NotNull Sound sound, boolean looping) {
        StreamingSoundInstance self = (StreamingSoundInstance) (Object) this;
        return CompletableFuture.completedFuture(self.getCurrentAudioStream());
    }
}