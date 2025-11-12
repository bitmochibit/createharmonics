package me.mochibit.createharmonics.mixin;

import me.mochibit.createharmonics.CreateHarmonicsMod;
import me.mochibit.createharmonics.Logger;
import me.mochibit.createharmonics.audio.PcmAudioStream;
import me.mochibit.createharmonics.audio.StreamRegistry;
import net.minecraft.Util;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

@Mixin(SoundBufferLibrary.class)
public class SoundBufferLibraryMixin {
    @Inject(at = @At("HEAD"), method = "getStream", cancellable = true)
    public void loadStreamed(@NotNull ResourceLocation pResourceLocation, boolean pIsWrapper, CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        if (!pResourceLocation.getNamespace().equals(CreateHarmonicsMod.MOD_ID)) return;

        String streamId = pResourceLocation.getPath().replaceAll("^sounds/", "").replaceAll("\\.ogg$", "");

        InputStream existingStream = StreamRegistry.INSTANCE.getStream(streamId);
        if (existingStream == null) {
            return;
        }

        cir.setReturnValue(CompletableFuture.supplyAsync(() -> {
            try {
                return new PcmAudioStream(existingStream);
            } catch (Exception e) {
                Logger.INSTANCE.err("SoundBufferLibraryMixin: Error creating audio stream: " + e.getMessage());
                throw new CompletionException(e);
            }
        }, Util.backgroundExecutor()));

        cir.cancel();
    }
}
