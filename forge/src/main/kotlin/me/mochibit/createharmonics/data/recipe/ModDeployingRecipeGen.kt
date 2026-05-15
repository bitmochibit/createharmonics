package me.mochibit.createharmonics.data.recipe

import com.simibubi.create.AllItems
import com.simibubi.create.api.data.recipe.DeployingRecipeGen
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe
import me.mochibit.createharmonics.CreateHarmonicsMod.MOD_ID
import me.mochibit.createharmonics.ForgeModEntryPoint
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.foundation.registry.ModItems
import me.mochibit.createharmonics.handler.RecordRepairHandler.calculateGlueRepairCost
import net.minecraft.data.PackOutput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient

class ModDeployingRecipeGen(
    output: PackOutput,
) : DeployingRecipeGen(output, MOD_ID) {
    init {
        RecordType.entries.filter { it.properties.recipe != null }.forEach {
            create<DeployerApplicationRecipe>("ethereal_record/00_crafting/${it.name.lowercase()}") { builder ->
                builder
                    .require(it.properties.recipe?.primaryIngredientProvider())
                    .require(it.properties.recipe?.secondaryIngredientProvider())
                    .output { ModItems.getEtherealRecordItem(it).get() }
            }
        }
    }
}
