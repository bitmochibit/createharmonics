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
        // <editor-fold desc="Ethereal record recipe gen">
        RecordType.entries.filter { it.properties.recipe != null }.forEach {
            create<DeployerApplicationRecipe>("ethereal_record/00_crafting/${it.name.lowercase()}") { builder ->
                builder
                    .require(it.properties.recipe?.primaryIngredientProvider())
                    .require(it.properties.recipe?.secondaryIngredientProvider())
                    .output { ModItems.getEtherealRecordItem(it).get() }
            }
        }
        // </editor-fold>

        // <editor-fold desc="Ethereal record glue repair gen">
        RecordType.entries.filter { it != RecordType.CREATIVE }.forEach {
            val damagedStack = ModItems.getBrokenEtherealRecordItem(it)?.get()?.defaultInstance ?: return@forEach
            create<DeployerApplicationRecipe>("ethereal_record/10_glue_repair/${it.name.lowercase()}") { builder ->
                val repairedStack = ModItems.getEtherealRecordItem(it).get().defaultInstance
                val costedGlue = ItemStack(AllItems.SUPER_GLUE.get())
                costedGlue.damageValue += calculateGlueRepairCost(it, it.uses)
                builder
                    .require(Ingredient.of(damagedStack))
                    .require(AllItems.SUPER_GLUE)
                    .output(repairedStack)
                    .output(costedGlue)
            }
        }
        // </editor-fold>

        // <editor-fold desc="Ethereal record material repair gen">
        RecordType.entries.filter { it.properties.repair != null }.forEach { recordType ->
            val damagedStack =
                ModItems
                    .getBrokenEtherealRecordItem(recordType)
                    ?.get()
                    ?.defaultInstance ?: return@forEach
            val repairedStack = ModItems.getEtherealRecordItem(recordType).get().defaultInstance

            recordType.properties.repair?.fullRepairIngredientProvider?.invoke()?.let { ingredient ->
                create<DeployerApplicationRecipe>("ethereal_record/20_full_repair/${recordType.name.lowercase()}") { builder ->
                    builder
                        .require(Ingredient.of(damagedStack))
                        .require(ingredient)
                        .output(repairedStack)
                }
            }

            recordType.properties.repair
                ?.partialRepairIngredientProvider
                ?.invoke()
                ?.let { (ingredient, repairFraction) ->
                    val partialStack = repairedStack.copy()
                    partialStack.damageValue = ((1 - repairFraction) * partialStack.maxDamage).toInt()
                    create<DeployerApplicationRecipe>("ethereal_record/30_partial_repair/${recordType.name.lowercase()}") { builder ->
                        builder
                            .require(Ingredient.of(damagedStack))
                            .require(ingredient)
                            .output(partialStack)
                    }
                }
        }
        // </editor-fold>
    }
}
