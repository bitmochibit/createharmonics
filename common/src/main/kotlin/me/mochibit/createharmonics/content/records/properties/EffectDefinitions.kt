package me.mochibit.createharmonics.content.records.properties

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import me.mochibit.createharmonics.CreateHarmonicsMod.MOD_ID
import me.mochibit.createharmonics.audio.effect.AudioEffect
import me.mochibit.createharmonics.audio.effect.BitCrushEffect
import me.mochibit.createharmonics.audio.effect.EQBand
import me.mochibit.createharmonics.audio.effect.EqualizerEffect
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.foundation.extension.asResource
import me.mochibit.createharmonics.foundation.registry.ModAudioEffectDefinitions
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

interface AudioEffectDefinition {
    fun codec(): MapCodec<out AudioEffectDefinition>
    fun create(): AudioEffect

    fun fidelity(): Float? = null

    fun flavors(): Set<RecordType.EffectAttribute> = emptySet()

    companion object {
        val CODEC: Codec<AudioEffectDefinition> by lazy {
            ModAudioEffectDefinitions.REGISTRY.byNameCodec().dispatch({ it.codec() }, { it })
        }
    }
}

data class BitCrushDefinition(val quality: Float) : AudioEffectDefinition {
    override fun codec() = CODEC
    override fun create(): AudioEffect = BitCrushEffect(quality = quality, scope = AudioEffect.Scope.INTRINSIC_EFFECT)
    override fun fidelity() = quality

    companion object {
        val CODEC: MapCodec<BitCrushDefinition> = RecordCodecBuilder.mapCodec { i ->
            i.group(Codec.floatRange(0f, 1f).fieldOf("quality").forGetter { it.quality }).apply(i, ::BitCrushDefinition)
        }
    }
}

data class EQBandDefinition(val frequency: Float, val quality: Float, val gain: Float) {
    fun toBand() = EQBand(frequency = frequency, quality = quality, gain = gain)

    companion object {
        val CODEC: Codec<EQBandDefinition> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.FLOAT.fieldOf("frequency").forGetter { it.frequency },
                Codec.FLOAT.optionalFieldOf("quality", 1f).forGetter { it.quality },
                Codec.FLOAT.fieldOf("gain").forGetter { it.gain },
            ).apply(i, ::EQBandDefinition)
        }
    }
}

data class EqualizerDefinition(val bands: List<EQBandDefinition>) : AudioEffectDefinition {
    override fun codec() = CODEC
    override fun create(): AudioEffect =
        EqualizerEffect(bands = bands.map { it.toBand() }, scope = AudioEffect.Scope.INTRINSIC_EFFECT)

    override fun flavors() =
        if (bands.any { it.frequency <= 120f && it.gain >= 3f }) setOf(RecordType.EffectAttribute.BASSY) else emptySet()

    companion object {
        val CODEC: MapCodec<EqualizerDefinition> = RecordCodecBuilder.mapCodec { i ->
            i.group(EQBandDefinition.CODEC.listOf().fieldOf("bands").forGetter { it.bands }).apply(i, ::EqualizerDefinition)
        }
    }
}
