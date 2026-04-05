package me.mochibit.createharmonics.data.recipe

import com.simibubi.create.api.data.recipe.DeployingRecipeGen
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe
import me.mochibit.createharmonics.CreateHarmonicsMod.MOD_ID
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.foundation.registry.ModItems
import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import java.util.concurrent.CompletableFuture

class ModDeployingRecipeGen(
    output: PackOutput,
    registries: CompletableFuture<HolderLookup.Provider>,
) : DeployingRecipeGen(output, registries, MOD_ID) {
    val discGeneratedRecipes: List<GeneratedRecipe> =
        RecordType.entries.filter { it.properties.recipe != null }.map {
            create("ethereal_record/${it.name.lowercase()}") { builder ->
                builder
                    .require(it.properties.recipe?.primaryIngredientProvider())
                    .require(it.properties.recipe?.secondaryIngredientProvider())
                    .output { ModItems.getEtherealRecordItem(it).get() }
            }
        }
}
