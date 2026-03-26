package me.mochibit.createharmonics.content.records

import com.simibubi.create.AllItems
import me.mochibit.createharmonics.audio.comp.SoundEventComposition
import me.mochibit.createharmonics.audio.effect.AudioEffect
import me.mochibit.createharmonics.audio.effect.BitCrushEffect
import me.mochibit.createharmonics.audio.effect.EQBand
import me.mochibit.createharmonics.audio.effect.EqualizerEffect
import me.mochibit.createharmonics.config.ModConfigs
import me.mochibit.createharmonics.foundation.extension.Tags
import me.mochibit.createharmonics.foundation.extension.asResource
import me.mochibit.createharmonics.foundation.extension.butOnForge
import me.mochibit.createharmonics.foundation.extension.resPath
import me.mochibit.createharmonics.foundation.extension.withPath
import me.mochibit.createharmonics.foundation.registry.platform.ModSoundRegistry
import me.mochibit.createharmonics.foundation.services.contentService
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.sounds.SoundEvents
import net.minecraft.tags.TagKey
import net.minecraft.util.RandomSource
import net.minecraft.world.item.crafting.Ingredient

enum class RecordType(
    val properties: Properties,
) {
    STONE(
        Properties(
            recipe =
                Properties.Recipe(
                    secondaryIngredientProvider = {
                        Ingredient.of(
                            Tags.Items withPath "stone",
                        )
                    },
                ),
            audioEffectsProvider = {
                listOf(
                    BitCrushEffect(quality = 0.3f, scope = AudioEffect.Scope.INTRINSIC_EFFECT),
                )
            },
            soundEventCompProvider = {
                listOf(
                    SoundEventComposition.SoundEventDef(
                        ModSoundRegistry.instance.slidingStoneSound,
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
                            Tags.Items withPath "ingots/gold",
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
                            Tags.Items withPath "gems/emerald",
                        )
                    },
                ),
            audioEffectsProvider = {
                listOf(
                    BitCrushEffect(quality = 0.6f, scope = AudioEffect.Scope.INTRINSIC_EFFECT),
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
                            Tags.Items withPath "gems/diamond",
                        )
                    },
                ),
            soundEventCompProvider = {
                listOf(
                    SoundEventComposition.SoundEventDef(
                        ModSoundRegistry.instance.glitterSoundEvent,
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
                            Tags.Items withPath "ingots/netherite",
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
                        scope = AudioEffect.Scope.INTRINSIC_EFFECT,
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
                    BitCrushEffect(quality = 0.9f, scope = AudioEffect.Scope.INTRINSIC_EFFECT),
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
            ModConfigs.server.getRecordDurability(this) ?: this.properties.defaultDurability

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
