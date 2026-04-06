package mixin;

import com.mojang.blaze3d.audio.Channel;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import me.mochibit.createharmonics.audio.stream.PausableAudioStream;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(Channel.class)
public abstract class ChannelMixin {
    @Shadow
    private AudioStream stream;
    
    @Inject(
            method = "unpause",
            at = @At("HEAD"),
            remap = false,
            cancellable = true)
    private void unpause(CallbackInfo ci) {
        if (this.stream instanceof PausableAudioStream pausableStream) {
            if (pausableStream.isPaused()) {
                ci.cancel();
            }
        }
    }

}


