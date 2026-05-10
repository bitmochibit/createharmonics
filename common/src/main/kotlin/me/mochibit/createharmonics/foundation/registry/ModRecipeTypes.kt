package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.ModRegistrate
import me.mochibit.createharmonics.content.records.DeployerRecordRepairRecipe
import me.mochibit.createharmonics.foundation.info
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.crafting.RecipeType

object ModRecipeTypes : CommonRegistry {
    val RECORD_FULL_MATERIAL_REPAIR =
        ModRegistrate.simple("record_full_mat_repair", Registries.RECIPE_TYPE) {
            object : RecipeType<DeployerRecordRepairRecipe.FullMaterialRepair> {
                override fun toString() = "record_full_mat_repair"
            }
        }

    val RECORD_PARTIAL_MATERIAL_REPAIR =
        ModRegistrate.simple("record_partial_mat_repair", Registries.RECIPE_TYPE) {
            object : RecipeType<DeployerRecordRepairRecipe.FullMaterialRepair> {
                override fun toString() = "record_partial_mat_repair"
            }
        }

    val RECORD_GLUE_MATERIAL_REPAIR =
        ModRegistrate.simple("record_glue_repair", Registries.RECIPE_TYPE) {
            object : RecipeType<DeployerRecordRepairRecipe.FullMaterialRepair> {
                override fun toString() = "record_glue_repair"
            }
        }

    override fun register() {
        "Registering recipe types".info()
    }
}
