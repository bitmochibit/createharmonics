package me.mochibit.createharmonics.content.item.record

import com.simibubi.create.AllItems
import me.mochibit.createharmonics.audio.comp.SoundEventComposition
import me.mochibit.createharmonics.audio.effect.AudioEffect
import me.mochibit.createharmonics.audio.effect.BitCrushEffect
import me.mochibit.createharmonics.audio.effect.EQBand
import me.mochibit.createharmonics.audio.effect.EqualizerEffect
import me.mochibit.createharmonics.registry.ModItems
import me.mochibit.createharmonics.registry.ModSounds
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.RandomSource
import net.minecraft.world.item.crafting.Ingredient
import net.minecraftforge.common.Tags

enum class RecordType(
    val properties: Properties,
) {
    STONE(
        Properties(
            uses = 20,
            recipe =
                Properties.Recipe(
                    secondaryIngredientProvider = { Ingredient.of(Tags.Items.STONE) },
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
                        volumeSupplier = { 0.5f },
                    ),
                )
            },
        ),
    ),

    GOLD(
        Properties(
            uses = 1,
            recipe =
                Properties.Recipe(
                    secondaryIngredientProvider = { Ingredient.of(Tags.Items.INGOTS_GOLD) },
                ),
        ),
    ),

    EMERALD(
        Properties(
            uses = 800,
            recipe =
                Properties.Recipe(
                    secondaryIngredientProvider = { Ingredient.of(Tags.Items.GEMS_EMERALD) },
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
        ),
    ),

    DIAMOND(
        Properties(
            uses = 1500,
            recipe =
                Properties.Recipe(
                    secondaryIngredientProvider = { Ingredient.of(Tags.Items.GEMS_DIAMOND) },
                ),
            soundEventCompProvider = {
                listOf(
                    SoundEventComposition.SoundEventDef(
                        ModSounds.GLITTER.get(),
                        looping = true,
                        relative = false,
                        volumeSupplier = { 0.7f },
                        probabilitySupplier = { 0.1f },
                    ),
                )
            },
        ),
    ),

    NETHERITE(
        Properties(
            uses = 2000,
            recipe =
                Properties.Recipe(
                    { Ingredient.of(ModItems.getEtherealRecordItem(DIAMOND)) },
                    { Ingredient.of(Tags.Items.INGOTS_NETHERITE) },
                ),
            audioEffectsProvider = {
                listOf(
                    EqualizerEffect(
                        // Bass boost 🪩
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
        ),
    ),
    ;

    data class Properties(
        val uses: Int = 100,
        val recipe: Recipe? = null,
        val audioEffectsProvider: () -> List<AudioEffect> = { listOf() },
        val soundEventCompProvider: () -> List<SoundEventComposition.SoundEventDef> = { listOf() },
    ) {
        data class Recipe(
            val primaryIngredientProvider: () -> Ingredient = { Ingredient.of(ModItems.BASE_RECORD.get()) },
            val secondaryIngredientProvider: () -> Ingredient = { Ingredient.EMPTY },
        )
    }
}
