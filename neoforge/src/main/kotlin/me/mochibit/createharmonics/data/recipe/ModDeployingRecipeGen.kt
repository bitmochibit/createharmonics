package me.mochibit.createharmonics.data.recipe

import com.simibubi.create.AllItems
import com.simibubi.create.api.data.recipe.DeployingRecipeGen
import me.mochibit.createharmonics.CreateHarmonicsMod.MOD_ID
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.foundation.extension.Tags
import me.mochibit.createharmonics.foundation.extension.withPath
import me.mochibit.createharmonics.foundation.registry.ModItems
import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import java.util.concurrent.CompletableFuture

class ModDeployingRecipeGen(
    output: PackOutput,
    registries: CompletableFuture<HolderLookup.Provider>,
) : DeployingRecipeGen(output, registries, MOD_ID) {

    private val baseRecord get() = Ingredient.of(ModItems.BASE_RECORD.get())

    private val craftingRecipes: Map<RecordType, Pair<() -> Ingredient, () -> Ingredient>> = mapOf(
        RecordType.STONE to ({ baseRecord } to { Ingredient.of(Tags.Items withPath "stones") }),
        RecordType.GOLD to ({ baseRecord } to { Ingredient.of(Tags.Items withPath "ingots/gold") }),
        RecordType.EMERALD to ({ baseRecord } to { Ingredient.of(Tags.Items withPath "gems/emerald") }),
        RecordType.DIAMOND to ({ baseRecord } to { Ingredient.of(Tags.Items withPath "gems/diamond") }),
        RecordType.NETHERITE to (
                { Ingredient.of(ModItems.getEtherealRecordItem(RecordType.DIAMOND).get()) } to
                        { Ingredient.of(Tags.Items withPath "ingots/netherite") }
                ),
        RecordType.BRASS to ({ baseRecord } to { Ingredient.of(AllItems.BRASS_INGOT) }),
    )

    init {
        craftingRecipes.forEach { (type, ingredients) ->
            create("ethereal_record/00_crafting/${type.name.lowercase()}") { builder ->
                builder
                    .require(ingredients.first())
                    .require(ingredients.second())
                    .output { ModItems.getEtherealRecordItem(type).get() }
            }
        }
    }
}