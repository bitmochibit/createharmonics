package me.mochibit.createharmonics.foundation.registry

import com.tterrag.registrate.util.entry.RegistryEntry
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.ModEventBus
import me.mochibit.createharmonics.foundation.extension.asResource
import net.minecraft.core.registries.Registries
import net.minecraft.sounds.SoundEvent
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object ModSounds : ForgeRegistry, HasModSound<RegistryObject<SoundEvent>> {
    private val SOUND_EVENTS: DeferredRegister<SoundEvent> =
        DeferredRegister.create(Registries.SOUND_EVENT, CreateHarmonicsMod.MOD_ID)

    override val slidingStoneSound = "sliding_stone".register()
    override val glitterSoundEvent = "glitter".register()

    override fun register() {
        SOUND_EVENTS.register(ModEventBus)
    }

    override fun String.register(): CrossPlatformRegistry.ConvertibleEntry<RegistryObject<SoundEvent>, SoundEvent> {
        val registered =
            SOUND_EVENTS.register(this) {
                SoundEvent.createVariableRangeEvent(
                    this.asResource(),
                )
            }
        return CrossPlatformRegistry.ConvertibleEntry(registered) { registered.get() }
    }
}
