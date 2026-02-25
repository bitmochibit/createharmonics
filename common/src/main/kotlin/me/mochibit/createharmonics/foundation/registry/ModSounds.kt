package me.mochibit.createharmonics.foundation.registry

import dev.architectury.registry.registries.DeferredRegister
import dev.architectury.registry.registries.RegistrySupplier
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.foundation.extension.asResource
import net.minecraft.core.registries.Registries
import net.minecraft.sounds.SoundEvent

object ModSounds : AutoRegistrable {
    private val SOUND_EVENTS: DeferredRegister<SoundEvent> =
        DeferredRegister.create(CreateHarmonicsMod.MOD_ID, Registries.SOUND_EVENT)

    val SLIDING_STONE = registerSoundEvent("sliding_stone")
    val GLITTER = registerSoundEvent("glitter")

    private fun registerSoundEvent(name: String): RegistrySupplier<SoundEvent> =
        SOUND_EVENTS.register(name) {
            SoundEvent.createVariableRangeEvent(
                name.asResource(),
            )
        }

    override fun register() {
        SOUND_EVENTS.register()
    }
}
