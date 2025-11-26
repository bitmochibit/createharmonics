package me.mochibit.createharmonics.data.recipe

import com.simibubi.create.api.data.recipe.DeployingRecipeGen
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.content.item.record.RecordType
import me.mochibit.createharmonics.registry.ModItemsRegistry
import net.minecraft.data.PackOutput

class ModDeployingRecipeGen(output: PackOutput) : DeployingRecipeGen(output, CreateHarmonicsMod.MOD_ID) {
    val DISC_GENERATED_RECIPES: List<GeneratedRecipe> = RecordType.entries.map {
        create<DeployerApplicationRecipe>("ethereal_record/${it.name.lowercase()}") { builder ->
            builder.require { ModItemsRegistry.BASE_RECORD.get() }
                .require(it.properties.recordIngredientProvider())
                .output { ModItemsRegistry.getEtherealRecordItem(it).get() }
        }
    }
}