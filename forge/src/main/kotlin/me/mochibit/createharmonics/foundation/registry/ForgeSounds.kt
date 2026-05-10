package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.ModEventBus
import me.mochibit.createharmonics.foundation.extension.asResource
import me.mochibit.createharmonics.foundation.registry.platform.ModSoundRegistry
import net.minecraft.core.registries.Registries
import net.minecraft.sounds.SoundEvent
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.RegistryObject

object ForgeSounds : ForgeRegistry, ForgeRegistryBinder<SoundEvent> {
    override val deferredRegister: DeferredRegister<SoundEvent> =
        DeferredRegister.create(Registries.SOUND_EVENT, CreateHarmonicsMod.MOD_ID)

    override fun register() {
        deferredRegister.register(ModEventBus)
        bindAll(ModSoundRegistry)
    }
}
