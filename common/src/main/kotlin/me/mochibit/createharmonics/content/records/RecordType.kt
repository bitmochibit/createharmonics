package me.mochibit.createharmonics.content.records

import com.simibubi.create.AllItems
import me.mochibit.createharmonics.audio.comp.SoundEventComposition
import me.mochibit.createharmonics.audio.effect.AudioEffect
import me.mochibit.createharmonics.audio.effect.BitCrushEffect
import me.mochibit.createharmonics.audio.effect.EQBand
import me.mochibit.createharmonics.audio.effect.EqualizerEffect
import me.mochibit.createharmonics.foundation.extension.asResource
import me.mochibit.createharmonics.foundation.extension.resPath
import me.mochibit.createharmonics.foundation.registry.ModSounds
import me.mochibit.createharmonics.foundation.shared.ConfigHelper
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.sounds.SoundEvents
import net.minecraft.tags.TagKey
import net.minecraft.util.RandomSource
import net.minecraft.world.item.crafting.Ingredient
import java.util.ServiceLoader

enum class RecordType(
    val properties: Properties,
) {
    STONE(
        Properties(
            recipe =
                Properties.Recipe(
                    secondaryIngredientProvider = {
                        Ingredient.of(
                            TagKey.create(Registries.ITEM, "c" resPath "stones"),
                        )
                    },
                ),
            audioEffectsProvider = {
                listOf(
                    BitCrushEffect(quality = 0.3f),
                )
            },
            soundEventCompProvider = {
                listOf(
                    SoundEventComposition.SoundEventDef(
                        ModSounds.SLIDING_STONE.get(),
                        looping = true,
                        relative = false,
                        volumeSupplier = { 0.25f },
                    ),
                )
            },
            defaultDurability = 20,
        ),
    ),

    GOLD(
        Properties(
            recipe =
                Properties.Recipe(
                    secondaryIngredientProvider = {
                        Ingredient.of(
                            TagKey.create(Registries.ITEM, "c" resPath "ingots/gold"),
                        )
                    },
                ),
            defaultDurability = 1,
        ),
    ),

    EMERALD(
        Properties(
            recipe =
                Properties.Recipe(
                    secondaryIngredientProvider = {
                        Ingredient.of(
                            TagKey.create(Registries.ITEM, "c" resPath "gems/emerald"),
                        )
                    },
                ),
            audioEffectsProvider = {
                listOf(
                    BitCrushEffect(quality = 0.6f),
                )
            },
            soundEventCompProvider = {
                listOf(
                    SoundEventComposition.SoundEventDef(
                        SoundEvents.VILLAGER_AMBIENT,
                        looping = false,
                        relative = false,
                        volumeSupplier = { 1.5f },
                        randomSource = RandomSource.create(),
                        probabilitySupplier = { 0.35f },
                    ),
                )
            },
            defaultDurability = 800,
        ),
    ),

    DIAMOND(
        Properties(
            recipe =
                Properties.Recipe(
                    secondaryIngredientProvider = {
                        Ingredient.of(
                            TagKey.create(Registries.ITEM, "c" resPath "gems/diamond"),
                        )
                    },
                ),
            soundEventCompProvider = {
                listOf(
                    SoundEventComposition.SoundEventDef(
                        ModSounds.GLITTER.get(),
                        looping = false,
                        relative = false,
                        volumeSupplier = { 0.7f },
                        probabilitySupplier = { 0.1f },
                    ),
                )
            },
            defaultDurability = 1500,
        ),
    ),

    NETHERITE(
        Properties(
            recipe =
                Properties.Recipe(
                    { Ingredient.of(BuiltInRegistries.ITEM.get("diamond_ethereal_record".asResource())) },
                    {
                        Ingredient.of(
                            TagKey.create(Registries.ITEM, "c" resPath "ingots/netherite"),
                        )
                    },
                ),
            audioEffectsProvider = {
                listOf(
                    EqualizerEffect(
                        bands =
                            listOf(
                                EQBand(frequency = 60f, quality = 0.7f, gain = 6f),
                                EQBand(frequency = 200f, quality = 1.0f, gain = 3f),
                                EQBand(frequency = 800f, quality = 1.5f, gain = -3f),
                                EQBand(frequency = 4000f, quality = 1.2f, gain = 2f),
                            ),
                    ),
                )
            },
            defaultDurability = 2000,
        ),
    ),

    BRASS(
        Properties(
            recipe =
                Properties.Recipe(
                    secondaryIngredientProvider = { Ingredient.of(AllItems.BRASS_INGOT) },
                ),
            audioEffectsProvider = {
                listOf(
                    BitCrushEffect(quality = 0.9f),
                )
            },
            defaultDurability = 250,
        ),
    ),

    CREATIVE(
        Properties(
            recipe = null,
            defaultDurability = 0,
        ),
    ),
    ;

    // Get uses from config, with fallback defaults matching config defaults
    val uses: Int
        get() =
            ConfigHelper.getRecordDurability(this) ?: this.properties.defaultDurability

    data class Properties(
        val recipe: Recipe? = null,
        val audioEffectsProvider: () -> List<AudioEffect> = { listOf() },
        val soundEventCompProvider: () -> List<SoundEventComposition.SoundEventDef> = { listOf() },
        val defaultDurability: Int = 250,
    ) {
        data class Recipe(
            val primaryIngredientProvider: () -> Ingredient = {
                Ingredient.of(
                    BuiltInRegistries.ITEM.get("ethereal_record_base".asResource()),
                )
            },
            val secondaryIngredientProvider: () -> Ingredient = { Ingredient.EMPTY },
        )
    }
}
