package mixin;

import com.mojang.blaze3d.audio.Channel;
import me.mochibit.createharmonics.audio.stream.PausableAudioStream;
import net.minecraft.client.sounds.AudioStream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
public abstract class ChannelMixin {
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

}


