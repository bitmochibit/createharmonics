package me.mochibit.createharmonics.data.recipe

import com.simibubi.create.api.data.recipe.DeployingRecipeGen
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.content.item.record.RecordType
import me.mochibit.createharmonics.registry.ModItems
import net.minecraft.data.PackOutput

class ModDeployingRecipeGen(
    output: PackOutput,
) : DeployingRecipeGen(output, CreateHarmonicsMod.MOD_ID) {
    val discGeneratedRecipes: List<GeneratedRecipe> =
        RecordType.entries.filter { it.properties.recipe != null }.map {
            create<DeployerApplicationRecipe>("ethereal_record/${it.name.lowercase()}") { builder ->
                builder
                    .require(it.properties.recipe?.primaryIngredientProvider())
                    .require(it.properties.recipe?.secondaryIngredientProvider())
                    .output { ModItems.getEtherealRecordItem(it).get() }
            }
        }
}
