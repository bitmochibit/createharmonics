package me.mochibit.createharmonics.handler

import com.simibubi.create.AllItems
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipeParams
import me.mochibit.createharmonics.content.records.DeployerRecordRepairRecipe
import me.mochibit.createharmonics.content.records.EtherealRecordItem
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.foundation.eventbus.CommonEvents
import me.mochibit.createharmonics.foundation.eventbus.EventBus
import me.mochibit.createharmonics.foundation.extension.asResource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeHolder
import java.util.Optional
import java.util.function.Supplier
import kotlin.math.ceil
import kotlin.math.ln

object RecordRepairHandler : CommonEventHandler {
    override fun setupEvents() {
        EventBus.onSync<CommonEvents.CreateEvents.CreateDeployerRecipeSearchEvent> { event ->
            val inv = event.recipeWrapper
            val beltItem = inv.getItem(0)
            val heldItem = inv.getItem(1)

            val recordItem = beltItem.item as? EtherealRecordItem ?: return@onSync
            val recordType = recordItem.recordType

            if (recordType == RecordType.CREATIVE) return@onSync

            if (beltItem.damageValue <= 0 && !recordItem.isRecordBroken()) return@onSync

            val params = ItemApplicationRecipeParams()

            event.addRecipe(
                Supplier {
                    if (heldItem.`is`(AllItems.SUPER_GLUE.get()) &&
                        heldItem.damageValue < heldItem.maxDamage
                    ) {
                        Optional.of(
                            holder(
                                "record_glue_repair",
                                DeployerRecordRepairRecipe.GlueRepair(
                                    params,
                                    inv,
                                    recordItem,
                                    event.deployerBe,
                                ),
                            ),
                        )
                    } else {
                        Optional.empty()
                    }
                },
                200,
            )

            recordType.properties.repair
                ?.fullRepairIngredientProvider
                ?.invoke()
                ?.takeIf { it.test(heldItem) }
                ?.let { ingredient ->
                    event.addRecipe(
                        Supplier {
                            Optional.of(
                                holder(
                                    "record_full_repair_${recordType.name.lowercase()}",
                                    DeployerRecordRepairRecipe.FullMaterialRepair(
                                        params,
                                        inv,
                                        ingredient,
                                    ),
                                ),
                            )
                        },
                        150,
                    )
                }

            recordType.properties.repair
                ?.partialRepairIngredientProvider
                ?.invoke()
                ?.takeIf { (ingredient, _) -> ingredient.test(heldItem) }
                ?.let { (ingredient, fraction) ->
                    event.addRecipe(
                        Supplier {
                            Optional.of(
                                holder(
                                    "record_partial_repair_${recordType.name.lowercase()}",
                                    DeployerRecordRepairRecipe.PartialMaterialRepair(
                                        params,
                                        inv,
                                        ingredient,
                                        fraction,
                                    ),
                                ),
                            )
                        },
                        120,
                    )
                }
        }
    }

    fun calculateGlueRepairCost(
        recordType: RecordType,
        currentDamage: Int,
    ): Int {
        val maxDamage = recordType.uses
        if (maxDamage <= 0) return 0
        val glueMaxDamage = ItemStack(AllItems.SUPER_GLUE.get()).maxDamage
        val repairRatio = currentDamage.toDouble() / maxDamage.toDouble()
        val logScale = ln(1.0 + maxDamage.toDouble() / glueMaxDamage.toDouble()) / ln(2.0)
        return ceil(logScale * repairRatio * 4.4).toInt().coerceIn(1, glueMaxDamage)
    }

    private fun holder(
        path: String,
        recipe: DeployerRecordRepairRecipe,
    ) = RecipeHolder(path.asResource(), recipe)
}
