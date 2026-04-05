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
import me.mochibit.createharmonics.foundation.extension.withPath
import me.mochibit.createharmonics.foundation.locale.LangProvider
import me.mochibit.createharmonics.foundation.locale.ModLang
import me.mochibit.createharmonics.foundation.registry.platform.ModSoundRegistry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.RandomSource
import net.minecraft.world.item.crafting.Ingredient
import java.util.Locale.getDefault

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
            effectAttributes =
                listOf(
                    Properties.EffectAttribute.MUDDY,
                    Properties.EffectAttribute.RUSTIC,
                ),
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
            effectAttributes =
                listOf(
                    Properties.EffectAttribute.CLEAN,
                ),
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
            effectAttributes =
                listOf(
                    Properties.EffectAttribute.MUDDY,
                    Properties.EffectAttribute.NOISY,
                ),
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
            effectAttributes =
                listOf(
                    Properties.EffectAttribute.CLEAN,
                    Properties.EffectAttribute.SHINY,
                ),
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
            effectAttributes =
                listOf(
                    Properties.EffectAttribute.CLEAN,
                    Properties.EffectAttribute.BASSY,
                ),
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
            effectAttributes =
                listOf(
                    Properties.EffectAttribute.MID,
                ),
        ),
    ),

    CREATIVE(
        Properties(
            recipe = null,
            defaultDurability = 0,
            effectAttributes =
                listOf(
                    Properties.EffectAttribute.CLEAN,
                ),
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
        val effectAttributes: List<EffectAttribute> = listOf(),
    ) {
        data class Recipe(
            val primaryIngredientProvider: () -> Ingredient = {
                Ingredient.of(
                    BuiltInRegistries.ITEM.get("ethereal_record_base".asResource()),
                )
            },
            val secondaryIngredientProvider: () -> Ingredient = { Ingredient.EMPTY },
        )

        enum class EffectAttribute(
            val style: Style,
            val qualityIndicator: Boolean = false,
        ) {
            BASSY(Style.EMPTY.withColor(TextColor.parseColor("#A53561").orThrow)),
            SHINY(Style.EMPTY.withColor(TextColor.parseColor("#55E1D9").orThrow)),
            RUSTIC(Style.EMPTY.withColor(TextColor.parseColor("#58693C").orThrow)),
            NOISY(Style.EMPTY.withColor(TextColor.parseColor("#17D960").orThrow)),

            CLEAN(Style.EMPTY.withColor(TextColor.parseColor("#F3F3F3").orThrow).withBold(true), true),
            MID(Style.EMPTY.withColor(TextColor.parseColor("#F3F3F3").orThrow).withItalic(true), true),
            MUDDY(Style.EMPTY.withColor(TextColor.parseColor("#F3F3F3").orThrow), true),
            ;

            fun translatedComponent(): MutableComponent =
                ModLang
                    .translate(
                        "tooltips.item.ethereal_record.effect_attribute.${this.name.lowercase()}",
                    ).component()
                    .withStyle(style)

            companion object : LangProvider {
                override fun provideLang(keyValueConsumer: (String, String) -> Unit) {
                    for (attribute in entries) {
                        val attrName = attribute.name.lowercase()
                        keyValueConsumer(
                            "tooltips.item.ethereal_record.effect_attribute.$attrName".withModNamespace(),
                            attrName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() },
                        )
                    }
                }
            }
        }
    }
}
