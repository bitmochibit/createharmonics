package me.mochibit.createharmonics.foundation.registry

import com.tterrag.registrate.util.entry.RegistryEntry
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.ModEventBus
import me.mochibit.createharmonics.foundation.extension.asResource
import net.minecraft.core.registries.Registries
import net.minecraft.sounds.SoundEvent
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.RegistryObject

object ModSounds : ForgeRegistry {
    private val SOUND_EVENTS: DeferredRegister<SoundEvent> =
        DeferredRegister.create(Registries.SOUND_EVENT, CreateHarmonicsMod.MOD_ID)

    val SLIDING_STONE = registerSoundEvent("sliding_stone")
    val GLITTER = registerSoundEvent("glitter")

    private fun registerSoundEvent(name: String): RegistryObject<SoundEvent> =
        SOUND_EVENTS.register(name) {
            SoundEvent.createVariableRangeEvent(
                name.asResource(),
            )
        }

    override fun register() {
        SOUND_EVENTS.register(ModEventBus)
    }
}
