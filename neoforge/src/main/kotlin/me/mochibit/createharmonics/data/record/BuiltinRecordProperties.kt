package me.mochibit.createharmonics.data.record

import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.content.records.properties.BitCrushDefinition
import me.mochibit.createharmonics.content.records.properties.EQBandDefinition
import me.mochibit.createharmonics.content.records.properties.EqualizerDefinition
import me.mochibit.createharmonics.content.records.properties.RecordProperties
import me.mochibit.createharmonics.content.records.properties.SoundEventData
import me.mochibit.createharmonics.foundation.registry.ModSounds
import net.minecraft.data.worldgen.BootstrapContext
import net.minecraft.sounds.SoundEvents

object BuiltinRecordProperties {
    fun bootstrap(ctx: BootstrapContext<RecordProperties>) {
        fun reg(t: RecordType, p: RecordProperties) = ctx.register(t.propertiesKey, p)

        reg(RecordType.STONE, RecordProperties(20, false,
            listOf(BitCrushDefinition(0.3f)),
            listOf(
                SoundEventData(
                    ModSounds.SLIDING_STONE.get(),
                    looping = true,
                    relative = false,
                    volume = 0.25f,
                    probability = 1f
                )
            ),
            setOf(RecordType.EffectAttribute.RUSTIC)))

        reg(RecordType.GOLD, RecordProperties(1, false, emptyList(), emptyList(), emptySet()))

        reg(RecordType.EMERALD, RecordProperties(800, false,
            listOf(BitCrushDefinition(0.6f)),
            listOf(SoundEventData(SoundEvents.VILLAGER_AMBIENT, looping = false, relative = false, volume = 1.5f, probability = 0.35f)),
            setOf(RecordType.EffectAttribute.NOISY)))

        reg(RecordType.DIAMOND, RecordProperties(1500, false, emptyList(),
            listOf(SoundEventData(ModSounds.GLITTER.get(), looping = false, relative = false, volume = 0.7f, probability = 0.1f)),
            setOf(RecordType.EffectAttribute.SHINY)))

        reg(RecordType.NETHERITE, RecordProperties(2000, false,
            listOf(EqualizerDefinition(listOf(
                EQBandDefinition(60f, 0.7f, 6f), EQBandDefinition(200f, 1.0f, 3f),
                EQBandDefinition(800f, 1.5f, -3f), EQBandDefinition(4000f, 1.2f, 2f),
            ))), emptyList(), emptySet()))

        reg(RecordType.BRASS, RecordProperties(250, false, listOf(BitCrushDefinition(0.9f)), emptyList(), emptySet()))

        reg(RecordType.CREATIVE, RecordProperties(250, true, emptyList(), emptyList(), emptySet()))
    }
}

