package me.mochibit.createharmonics.foundation.registry

import com.mojang.serialization.Codec
import com.simibubi.create.foundation.data.CreateRegistrate
import com.tterrag.registrate.util.entry.RegistryEntry
import me.mochibit.createharmonics.ModRegistrate
import me.mochibit.createharmonics.foundation.info
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.Registries

object ModDataComponents : CommonRegistry {
    val RECORD_URL = ModRegistrate.dataComponent("record_url", Codec.STRING)
    val CRAFTED_WITH = ModRegistrate.dataComponent("crafted_with", Codec.STRING)

    override fun register() {
        "Registering data components..".info()
    }

    inline fun <reified T> CreateRegistrate.dataComponent(
        name: String,
        codec: Codec<T>,
    ): RegistryEntry<DataComponentType<*>, DataComponentType<T>> =
        this.simple(name, Registries.DATA_COMPONENT_TYPE) {
            DataComponentType
                .builder<T>()
                .persistent(codec)
                .build()
        }
}
