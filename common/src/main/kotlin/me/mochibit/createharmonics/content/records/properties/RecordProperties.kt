package me.mochibit.createharmonics.content.records.properties

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.foundation.extension.asResource
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.util.ExtraCodecs

/**
 * Record properties
 * Data class — a value holder; `equals`, `hashCode`, `toString` and `copy` are generated.
 *
 * @constructor Creates a new RecordProperties
 * @property durability
 * @property unbreakable
 * @property audioEffects the audio effects
 * @property soundEvents the sound events
 * @property explicitAttributes the explicit attributes
 */
data class RecordProperties(
    val durability: Int,
    val unbreakable: Boolean,
    val audioEffects: List<AudioEffectDefinition>,
    val soundEvents: List<SoundEventData>,
    val explicitAttributes: Set<RecordType.EffectAttribute>,
) {
    val attributes: Set<RecordType.EffectAttribute> by lazy {
        val fidelity = audioEffects.mapNotNull { it.fidelity() }.minOrNull() ?: 1f
        val tier = when {
            fidelity >= 0.99f -> RecordType.EffectAttribute.CLEAN
            fidelity >= 0.75f -> RecordType.EffectAttribute.MID
            else -> RecordType.EffectAttribute.MUDDY
        }
        buildSet {
            if (explicitAttributes.none { it.qualityIndicator }) add(tier)
            audioEffects.forEach { addAll(it.flavors()) }
            addAll(explicitAttributes)
        }
    }

    /**
     * Companion
     * Companion object — holds factory methods and constants for the enclosing class.
     *
     * @constructor Creates a new Companion
     */
    companion object {
        val KEY: ResourceKey<Registry<RecordProperties>> =
            ResourceKey.createRegistryKey("record_properties".asResource())

        val FALLBACK = RecordProperties(250, false, emptyList(), emptyList(), emptySet())

        val CODEC: Codec<RecordProperties> = RecordCodecBuilder.create { i ->
            i.group(
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("durability", 250).forGetter { it.durability },
                Codec.BOOL.optionalFieldOf("unbreakable", false).forGetter { it.unbreakable },
                AudioEffectDefinition.CODEC.listOf().optionalFieldOf("audio_effects", emptyList()).forGetter { it.audioEffects },
                SoundEventData.CODEC.listOf().optionalFieldOf("sound_events", emptyList()).forGetter { it.soundEvents },
                RecordType.EffectAttribute.CODEC.listOf()
                    .xmap({ it.toSet() }, { it.toList() })
                    .optionalFieldOf("attributes", emptySet()).forGetter { it.explicitAttributes },
            ).apply(i, ::RecordProperties)
        }
    }
}