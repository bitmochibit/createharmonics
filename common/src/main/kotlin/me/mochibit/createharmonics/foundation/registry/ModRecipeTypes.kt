package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.ModRegistrate
import me.mochibit.createharmonics.content.records.DeployerRecordRepairRecipe
import me.mochibit.createharmonics.foundation.extension.asResource
import me.mochibit.createharmonics.foundation.info
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.crafting.RecipeType

object ModRecipeTypes : CommonRegistry {
    val RECORD_REPAIR =
        ModRegistrate.simple("record_repair", Registries.RECIPE_TYPE) {
            RecipeType.simple<DeployerRecordRepairRecipe>(
                "record_repair".asResource(),
            )
        }

    override fun register() {
        "Registering recipe types".info()
    }
}
