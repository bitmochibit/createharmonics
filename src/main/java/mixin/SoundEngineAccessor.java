package mixin;

import net.minecraft.client.sounds.SoundBufferLibrary;
import org.spongepowered.asm.mixin.gen.Accessor;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.sounds.SoundEngine.class)
public interface SoundEngineAccessor {
    @Accessor
    SoundBufferLibrary getSoundBuffers();
}
