package me.mochibit.createharmonics.content.item.record

import com.simibubi.create.AllItems
import com.simibubi.create.AllSoundEvents
import me.mochibit.createharmonics.audio.comp.SoundEventComposition
import me.mochibit.createharmonics.audio.effect.AudioEffect
import me.mochibit.createharmonics.audio.effect.BitCrushEffect
import me.mochibit.createharmonics.audio.effect.EQBand
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.effect.EqualizerEffect
import me.mochibit.createharmonics.audio.effect.LowPassFilterEffect
import me.mochibit.createharmonics.audio.effect.MixerEffect
import me.mochibit.createharmonics.audio.effect.ReverbEffect
import me.mochibit.createharmonics.audio.effect.VolumeEffect
import me.mochibit.createharmonics.registry.ModSoundsRegistry
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
            recordIngredientProvider = { Ingredient.of(Tags.Items.STONE) },
            audioEffectsProvider = {
                listOf(
                    BitCrushEffect(quality = 0.3f),
                )
            },
            soundEventCompProvider = {
                listOf(
                    SoundEventComposition.SoundEventDef(
                        ModSoundsRegistry.SLIDING_STONE.get(),
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
            recordIngredientProvider = { Ingredient.of(Tags.Items.INGOTS_GOLD) },
        ),
    ),

    EMERALD(
        Properties(
            uses = 800,
            recordIngredientProvider = { Ingredient.of(Tags.Items.GEMS_EMERALD) },
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
                        volumeSupplier = { 1.0f },
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
            recordIngredientProvider = { Ingredient.of(Tags.Items.GEMS_DIAMOND) },
            soundEventCompProvider = {
                listOf(
                    SoundEventComposition.SoundEventDef(
                        ModSoundsRegistry.SPARKLING.get(),
                        looping = true,
                        relative = false,
                        volumeSupplier = { 0.7f },
                    ),
                )
            },
        ),
    ),

    NETHERITE(
        Properties(
            uses = 2000,
            recordIngredientProvider = { Ingredient.of(Tags.Items.INGOTS_NETHERITE) },
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
            recordIngredientProvider = { Ingredient.of(AllItems.BRASS_INGOT) },
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
        val recordIngredientProvider: () -> Ingredient = { Ingredient.EMPTY },
        val audioEffectsProvider: () -> List<AudioEffect> = { listOf() },
        val soundEventCompProvider: () -> List<SoundEventComposition.SoundEventDef> = { listOf() },
    )
}
