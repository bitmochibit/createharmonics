package me.mochibit.createharmonics.foundation.registry

import dev.architectury.registry.registries.DeferredRegister
import me.mochibit.createharmonics.CreateHarmonicsMod
import net.minecraft.core.registries.Registries

object ModMenuTypes : Registrable {
    private val MENUS = DeferredRegister.create(CreateHarmonicsMod.MOD_ID, Registries.MENU)

    override fun register() {
        MENUS.register()
    }
}
