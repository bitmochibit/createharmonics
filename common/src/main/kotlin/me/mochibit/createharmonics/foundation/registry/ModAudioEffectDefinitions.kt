package me.mochibit.createharmonics.foundation.registry

import com.mojang.serialization.Lifecycle
import com.mojang.serialization.MapCodec
import me.mochibit.createharmonics.ModRegistrate
import me.mochibit.createharmonics.content.records.properties.AudioEffectDefinition
import me.mochibit.createharmonics.content.records.properties.BitCrushDefinition
import me.mochibit.createharmonics.content.records.properties.EQBandDefinition
import me.mochibit.createharmonics.content.records.properties.EqualizerDefinition
import me.mochibit.createharmonics.foundation.extension.asResource
import me.mochibit.createharmonics.foundation.info
import net.minecraft.core.MappedRegistry
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey

@AutoRegister(RegistryPhase.PRE_FREEZE)
object ModAudioEffectDefinitions : Registrable {
    val KEY: ResourceKey<Registry<MapCodec<out AudioEffectDefinition>>> =
        ResourceKey.createRegistryKey("audio_effect_definition".asResource())

    val REGISTRY: MappedRegistry<MapCodec<out AudioEffectDefinition>> =
        MappedRegistry(KEY, Lifecycle.stable())

    val BIT_CRUSH = define("bit_crush", BitCrushDefinition.CODEC)
    val EQUALIZER = define("equalizer", EqualizerDefinition.CODEC)

    private fun <T : AudioEffectDefinition> define(id: String, codec: MapCodec<T>): MapCodec<T> =
        codec.also { Registry.register(REGISTRY, id.asResource(), it) }

    override fun register(registry: Registry<*>?) {
        "Registering effect types".info()
    }
}