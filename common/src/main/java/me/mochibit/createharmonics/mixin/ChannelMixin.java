package me.mochibit.createharmonics.mixin;

import com.mojang.blaze3d.audio.Channel;
import me.mochibit.createharmonics.audio.stream.AudioLatencyConfig;
import me.mochibit.createharmonics.audio.stream.PausableAudioStream;
import me.mochibit.createharmonics.audio.stream.PcmAudioStream;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.ChannelAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import javax.sound.sampled.AudioFormat;

@Mixin(Channel.class)
public abstract class ChannelMixin {
    @Shadow private int streamingBufferSize;
    @Shadow
    private AudioStream stream;

    @Shadow
    public abstract boolean stopped();

    @Inject(
            method = "unpause",
            at = @At("HEAD"),
            cancellable = true)
    private void unpause(CallbackInfo ci) {
        if (this.stream instanceof PausableAudioStream pausableStream) {
            if (pausableStream.isPaused()) {
                ci.cancel();
            }
        }
    }


    @Invoker("pumpBuffers")
    abstract void createharmonics$pumpBuffers(int readCount);


    @Inject(method = "attachBufferStream", at = @At("HEAD"), cancellable = true)
    private void createharmonics$attachBufferStream(AudioStream stream, CallbackInfo ci) {
        if (!(stream instanceof PcmAudioStream pcmStream)) return;

        this.stream = stream;
        this.streamingBufferSize = pcmStream.getReadBufferSize();
        createharmonics$pumpBuffers(AudioLatencyConfig.AL_QUEUED_BUFFERS);

        ci.cancel();
    }

}


