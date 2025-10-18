package me.mochibit.createharmonics.mixin;

import com.mojang.blaze3d.audio.OggAudioStream;
import me.mochibit.createharmonics.CreateHarmonicsMod;
import me.mochibit.createharmonics.audio.StreamRegistry;
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

        System.out.println("SoundBufferLibraryMixin: Intercepting sound request for " + pResourceLocation);

        // Normalize the resource location:
        // Minecraft transforms "youtube_hash" -> "sounds/youtube_hash.ogg"
        // We need to reverse this to match our registry key
        String path = pResourceLocation.getPath();

        // Remove "sounds/" prefix if present
        if (path.startsWith("sounds/")) {
            path = path.substring("sounds/".length());
        }

        // Remove ".ogg" extension if present
        if (path.endsWith(".ogg")) {
            path = path.substring(0, path.length() - ".ogg".length());
        }

        ResourceLocation normalizedLocation = ResourceLocation.fromNamespaceAndPath(
                pResourceLocation.getNamespace(),
                path
        );

        System.out.println("SoundBufferLibraryMixin: Normalized to " + normalizedLocation);

        // Look up the stream from the registry using the normalized resource location
        InputStream existingStream = StreamRegistry.INSTANCE.getStream(normalizedLocation);
        if (existingStream == null) {
            System.err.println("SoundBufferLibraryMixin: No stream found for " + normalizedLocation);
            return;
        }

        System.out.println("SoundBufferLibraryMixin: Found stream in registry for " + normalizedLocation);

        cir.setReturnValue(CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("SoundBufferLibraryMixin: Creating OggAudioStream from registry stream");
                return pIsWrapper ? new LoopingAudioStream(OggAudioStream::new, existingStream)
                        : new OggAudioStream(existingStream);
            } catch (IOException iOException) {
                System.err.println("SoundBufferLibraryMixin: Error creating audio stream: " + iOException.getMessage());
                throw new CompletionException(iOException);
            }
        }, Util.backgroundExecutor()));

        cir.cancel();
    }
}
