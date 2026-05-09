package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.ModEventBus
import me.mochibit.createharmonics.ModRegistrate
import me.mochibit.createharmonics.foundation.registry.platform.ModSoundRegistry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.sounds.SoundEvent
import net.neoforged.neoforge.registries.DeferredRegister

object NeoSoundRegistry : NeoforgeRegistryBinder<SoundEvent>, NeoforgeRegistry {
    override val deferredRegister =
        DeferredRegister.create(
            BuiltInRegistries.SOUND_EVENT,
            CreateHarmonicsMod.MOD_ID,
        )


    override fun register() {
        deferredRegister.register(ModEventBus)
        bindAll(ModSoundRegistry)
    }
}
