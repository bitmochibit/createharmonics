package me.mochibit.createharmonics.handler

import com.simibubi.create.AllItems
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipeParams
import me.mochibit.createharmonics.content.records.DeployerRecordRepairRecipe
import me.mochibit.createharmonics.content.records.EtherealRecordItem
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.foundation.extension.asResource
import me.mochibit.createharmonics.foundation.registry.ModRecipeTypes
import me.mochibit.createharmonics.foundation.services.eventService
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeHolder
import java.util.*
import java.util.function.Supplier
import kotlin.math.ceil
import kotlin.math.ln

object RecordRepairHandler : CommonEventHandler {
    override fun setupEvents() {
        eventService.onCreateDeployerRecipeSearch { be, input, addRecipe ->
            val record = input.getItem(0)
            val held = input.getItem(1)
            if (record.item !is EtherealRecordItem) return@onCreateDeployerRecipeSearch
            val level = be.level ?: return@onCreateDeployerRecipeSearch

            level.recipeManager.getAllRecipesFor(ModRecipeTypes.RECORD_REPAIR.get())
                .filter { it.value().matches(record, held) }
                .forEach { holder ->
                    addRecipe(
                        Supplier {
                            Optional.of(
                                RecipeHolder(
                                    holder.id(),
                                    DeployerRecordRepairRecipe(ItemApplicationRecipeParams(), input, holder.value(), be),
                                ),
                            )
                        },
                        holder.value().kind.priority,
                    )
                }
        }
    }
}