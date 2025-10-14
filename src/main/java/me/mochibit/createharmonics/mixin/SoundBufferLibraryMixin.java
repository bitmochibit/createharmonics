package me.mochibit.createharmonics.mixin;

import com.mojang.blaze3d.audio.OggAudioStream;
import me.mochibit.createharmonics.CreateHarmonicsMod;
import me.mochibit.createharmonics.audio.YoutubePlayer;
import net.minecraft.Util;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Mixin(SoundBufferLibrary.class)
public class SoundBufferLibraryMixin {
    @Inject(at = @At("HEAD"), method = "getStream", cancellable = true)
    public void loadStreamed(@NotNull ResourceLocation pResourceLocation, boolean pIsWrapper, CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        if (!pResourceLocation.getNamespace().equals(CreateHarmonicsMod.MOD_ID)) return;

        InputStream inputStream = YoutubePlayer.INSTANCE.streamAudio(
                "https://www.youtube.com/watch?v=2lwRKzUVSm8",
                "ogg",
                48000,
                2,
                65535
        );

        cir.setReturnValue(CompletableFuture.supplyAsync(() -> {
            try {
                return pIsWrapper ? new LoopingAudioStream(OggAudioStream::new, inputStream)
                        : new OggAudioStream(inputStream);
            } catch (IOException iOException) {
                throw new CompletionException(iOException);
            }
        }, Util.backgroundExecutor()));

        cir.cancel();
    }
}
