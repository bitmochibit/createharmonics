package mixin;

import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.gen.Accessor;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.sounds.SoundManager.class)
public interface SoundManagerAccessor {
    @Accessor
    SoundEngine getSoundEngine();
}
