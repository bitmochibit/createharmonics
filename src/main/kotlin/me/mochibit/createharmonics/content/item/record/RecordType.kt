package me.mochibit.createharmonics.content.item.record

import com.simibubi.create.AllItems
import com.simibubi.create.AllSoundEvents
import me.mochibit.createharmonics.audio.comp.SoundEventComposition
import me.mochibit.createharmonics.audio.effect.AudioEffect
import me.mochibit.createharmonics.audio.effect.BitCrushEffect
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.effect.LowPassFilterEffect
import me.mochibit.createharmonics.audio.effect.MixerEffect
import me.mochibit.createharmonics.audio.effect.ReverbEffect
import me.mochibit.createharmonics.audio.effect.VolumeEffect
import me.mochibit.createharmonics.registry.ModSoundsRegistry
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.crafting.Ingredient
import net.minecraftforge.common.Tags

enum class RecordType(
    val properties: Properties,
) {
    STONE(
        Properties(
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
            recordIngredientProvider = { Ingredient.of(Tags.Items.INGOTS_GOLD) },
            audioEffectsProvider = {
                listOf(
                    ReverbEffect(roomSize = 0.3f, damping = 0.3f, wetMix = 0.2f),
                    BitCrushEffect(quality = 0.7f),
                )
            },
        ),
    ),

    EMERALD(
        Properties(
            recordIngredientProvider = { Ingredient.of(Tags.Items.GEMS_EMERALD) },
            audioEffectsProvider = {
                listOf(
                    ReverbEffect(roomSize = 0.4f, damping = 0.5f, wetMix = 0.25f),
                    BitCrushEffect(quality = 0.85f),
                )
            },
        ),
    ),

    DIAMOND(
        Properties(
            recordIngredientProvider = { Ingredient.of(Tags.Items.GEMS_DIAMOND) },
            audioEffectsProvider = {
                listOf(
                    ReverbEffect(roomSize = 0.5f, damping = 0.6f, wetMix = 0.3f),
                    BitCrushEffect(quality = 0.95f),
                )
            },
        ),
    ),

    NETHERITE(
        Properties(
            recordIngredientProvider = { Ingredient.of(Tags.Items.INGOTS_NETHERITE) },
            audioEffectsProvider = {
                listOf(
                    ReverbEffect(roomSize = 0.6f, damping = 0.7f, wetMix = 0.35f),
                    BitCrushEffect(quality = 1.0f), // No degradation
                )
            },
        ),
    ),

    BRASS(
        Properties(
            recordIngredientProvider = { Ingredient.of(AllItems.BRASS_INGOT) },
            audioEffectsProvider = {
                listOf(
                    LowPassFilterEffect(cutoffFrequency = 8000f, resonance = 0.4f),
                    ReverbEffect(roomSize = 0.35f, damping = 0.4f, wetMix = 0.2f),
                    BitCrushEffect(quality = 0.8f),
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
