package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.ModEventBus
import me.mochibit.createharmonics.foundation.registry.platform.ModDataComponents
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.Registries
import net.neoforged.neoforge.registries.DeferredRegister

object NeoDataComponents : NeoforgeRegistryBinder<DataComponentType<*>>, NeoforgeRegistry {
    override val deferredRegister =
        DeferredRegister.createDataComponents(
            Registries.DATA_COMPONENT_TYPE,
            CreateHarmonicsMod.MOD_ID,
        )

    override fun register() {
        deferredRegister.register(ModEventBus)
        bindAll(ModDataComponents)
    }
}
