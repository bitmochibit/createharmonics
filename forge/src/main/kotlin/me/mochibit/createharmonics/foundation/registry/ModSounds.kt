package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.ModEventBus
import me.mochibit.createharmonics.foundation.extension.asResource
import me.mochibit.createharmonics.foundation.registry.platform.ModSoundRegistry
import net.minecraft.core.registries.Registries
import net.minecraft.sounds.SoundEvent
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.RegistryObject

object ModSounds : ForgeRegistry, ModSoundRegistry<RegistryObject<SoundEvent>>() {
    private val SOUND_EVENTS: DeferredRegister<SoundEvent> =
        DeferredRegister.create(Registries.SOUND_EVENT, CreateHarmonicsMod.MOD_ID)

    override val slidingStoneSound: SoundEvent by registerEntry("sliding_stone")
    override val glitterSoundEvent: SoundEvent by registerEntry("glitter")

    override fun register() {
        SOUND_EVENTS.register(ModEventBus)
    }

    override fun registerEntry(name: String): ConvertibleEntry<RegistryObject<SoundEvent>, SoundEvent> {
        val registered =
            SOUND_EVENTS.register(name) {
                SoundEvent.createVariableRangeEvent(
                    name.asResource(),
                )
            }
        return ConvertibleEntry(this@ModSounds, registered) { registered.get() }
    }
}
