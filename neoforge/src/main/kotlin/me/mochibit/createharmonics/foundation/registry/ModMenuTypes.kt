package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.ModEventBus
import net.minecraft.core.registries.Registries
import net.neoforged.neoforge.registries.DeferredRegister

object ModMenuTypes : NeoforgeRegistry {
    private val MENUS = DeferredRegister.create(Registries.MENU, CreateHarmonicsMod.MOD_ID)

    override fun register() {
        MENUS.register(ModEventBus)
    }
}
