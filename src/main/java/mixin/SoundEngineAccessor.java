package mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundBufferLibrary;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.sounds.SoundEngine.class)
public interface SoundEngineAccessor {
    @Accessor
    SoundBufferLibrary getSoundBuffers();

    @Accessor
    Map<SoundInstance, ChannelAccess.ChannelHandle> getInstanceToChannel();
}
