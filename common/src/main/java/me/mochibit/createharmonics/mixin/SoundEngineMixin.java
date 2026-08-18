package me.mochibit.createharmonics.mixin;

import me.mochibit.createharmonics.audio.instance.AudioPlayerSoundInstance;
import me.mochibit.createharmonics.audio.stream.AudioLatencyConfig;
import me.mochibit.createharmonics.audio.stream.PcmAudioStream;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.client.sounds.SoundEngine.class)
public abstract class SoundEngineMixin {
    @Inject(method = "calculatePitch", at = @At("HEAD"), cancellable = true)
    private void createharmonics$calculatePitch(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (sound instanceof AudioPlayerSoundInstance) {
            cir.setReturnValue(sound.getPitch());
        }
    }
}
