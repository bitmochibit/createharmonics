package me.mochibit.createharmonics.content.records.properties

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import me.mochibit.createharmonics.audio.comp.SoundEventComposition
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.sounds.SoundEvent
import net.minecraft.util.RandomSource

/**
 * Sound event data
 * Data class — a value holder; `equals`, `hashCode`, `toString` and `copy` are generated.
 *
 * @constructor Creates a new SoundEventData
 * @property sound
 * @property looping
 * @property relative
 * @property volume
 * @property probability
 */
data class SoundEventData(
    val sound: SoundEvent,
    val looping: Boolean,
    val relative: Boolean,
    val volume: Float,
    val probability: Float,
) {
    /**
     * Creates a new
     *
     * @return the sound event def
     */
    fun create(): SoundEventComposition.SoundEventDef =
        if (probability >= 1f) {
            SoundEventComposition.SoundEventDef(
                sound, looping = looping, relative = relative, volumeSupplier = { volume },
            )
        } else {
            SoundEventComposition.SoundEventDef(
                sound, looping = looping, relative = relative, volumeSupplier = { volume },
                randomSource = RandomSource.create(), probabilitySupplier = { probability },
            )
        }

    /**
     * Companion
     * Companion object — holds factory methods and constants for the enclosing class.
     *
     * @constructor Creates a new Companion
     */
    companion object {
        val CODEC: Codec<SoundEventData> = RecordCodecBuilder.create { i ->
            i.group(
                BuiltInRegistries.SOUND_EVENT.byNameCodec().fieldOf("sound").forGetter { it.sound },
                Codec.BOOL.optionalFieldOf("looping", false).forGetter { it.looping },
                Codec.BOOL.optionalFieldOf("relative", false).forGetter { it.relative },
                Codec.FLOAT.optionalFieldOf("volume", 1f).forGetter { it.volume },
                Codec.floatRange(0f, 1f).optionalFieldOf("probability", 1f).forGetter { it.probability },
            ).apply(i, ::SoundEventData)
        }
    }
}

