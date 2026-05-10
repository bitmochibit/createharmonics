package me.mochibit.createharmonics.content.records

import com.simibubi.create.AllItems
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe
import com.simibubi.create.content.processing.recipe.ProcessingOutput
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder
import me.mochibit.createharmonics.foundation.registry.ModRecipeTypes
import me.mochibit.createharmonics.handler.RecordRepairHandler
import net.minecraft.core.RegistryAccess
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraftforge.items.wrapper.RecipeWrapper

sealed class DeployerRecordRepairRecipe(
    params: ProcessingRecipeBuilder.ProcessingRecipeParams,
    val capturedInput: RecipeWrapper,
) : DeployerApplicationRecipe(params) {
    class GlueRepair(
        params: ProcessingRecipeBuilder.ProcessingRecipeParams,
        capturedInput: RecipeWrapper,
        private val recordItem: EtherealRecordItem,
        val capturedBe: BlockEntity,
    ) : DeployerRecordRepairRecipe(params, capturedInput) {
        override fun matches(
            input: RecipeWrapper,
            level: Level,
        ): Boolean {
            val record = input.getItem(0)
            val glue = input.getItem(1)
            return record.item == recordItem &&
                glue.`is`(AllItems.SUPER_GLUE.get()) &&
                record.damageValue > 0 &&
                glue.damageValue < glue.maxDamage
        }

        override fun getResultItem(registryAccess: RegistryAccess): ItemStack {
            val record = capturedInput.getItem(0)
            val glue = capturedInput.getItem(1)
            val isRecordBroken = recordItem.isRecordBroken()

            val cost =
                RecordRepairHandler.calculateGlueRepairCost(
                    recordItem.recordType,
                    if (isRecordBroken) record.maxDamage else record.damageValue,
                )
            val newGlueDamage = glue.damageValue + cost
            if (newGlueDamage >= glue.maxDamage) {
                glue.shrink(1)
            } else {
                glue.damageValue = newGlueDamage
            }

            capturedBe.level?.playSound(
                null,
                capturedBe.blockPos,
                SoundEvents.SLIME_BLOCK_BREAK,
                SoundSource.PLAYERS,
                1.0f,
                1.0f,
            )

            if (isRecordBroken) {
                return RecordUtilities.fromBrokenRecordStack(record)
            }

            return record.copy().also { it.damageValue = 0 }
        }

        override fun rollResults(rollableResults: List<ProcessingOutput?>): List<ItemStack?> {
            val repairedRecord = this.getResultItem(RegistryAccess.EMPTY)
            return mutableListOf(repairedRecord)
        }

        override fun getType(): RecipeType<*> = ModRecipeTypes.RECORD_GLUE_MATERIAL_REPAIR.get()
    }

    class FullMaterialRepair(
        params: ProcessingRecipeBuilder.ProcessingRecipeParams,
        capturedInput: RecipeWrapper,
        private val ingredient: Ingredient,
    ) : DeployerRecordRepairRecipe(params, capturedInput) {
        override fun matches(
            input: RecipeWrapper,
            level: Level,
        ): Boolean {
            val record = input.getItem(0)
            val material = input.getItem(1)
            return record.item is EtherealRecordItem &&
                ingredient.test(material) &&
                record.damageValue > 0
        }

        override fun getResultItem(registryAccess: RegistryAccess): ItemStack {
            val record = capturedInput.getItem(0)
            val recordItem = record.item as? EtherealRecordItem ?: return ItemStack.EMPTY

            if (recordItem.isRecordBroken()) {
                return RecordUtilities.fromBrokenRecordStack(record)
            }

            return record.copy().also { it.damageValue = 0 }
        }

        override fun getType(): RecipeType<*> = ModRecipeTypes.RECORD_FULL_MATERIAL_REPAIR.get()

        override fun rollResults(rollableResults: List<ProcessingOutput?>): List<ItemStack?> {
            val repairedRecord = this.getResultItem(RegistryAccess.EMPTY)
            return mutableListOf(repairedRecord)
        }
    }

    class PartialMaterialRepair(
        params: ProcessingRecipeBuilder.ProcessingRecipeParams,
        capturedInput: RecipeWrapper,
        private val ingredient: Ingredient,
        private val repairFraction: Float,
    ) : DeployerRecordRepairRecipe(params, capturedInput) {
        override fun matches(
            input: RecipeWrapper,
            level: Level,
        ): Boolean {
            val record = input.getItem(0)
            val material = input.getItem(1)
            return record.item is EtherealRecordItem &&
                ingredient.test(material) &&
                record.damageValue > 0
        }

        override fun getResultItem(registryAccess: RegistryAccess): ItemStack {
            val record = capturedInput.getItem(0)
            val recordItem = record.item as? EtherealRecordItem ?: return ItemStack.EMPTY

            if (recordItem.isRecordBroken()) {
                val repaired = RecordUtilities.fromBrokenRecordStack(record)
                val repairAmount = (repairFraction * repaired.maxDamage).toInt().coerceAtLeast(1)
                repaired.damageValue = (repaired.maxDamage - repairAmount - 1).coerceAtLeast(0)
                return repaired
            }

            val repairAmount = (repairFraction * record.maxDamage).toInt().coerceAtLeast(1)
            return record.copy().also {
                it.damageValue = (record.damageValue - repairAmount).coerceAtLeast(0)
            }
        }

        override fun rollResults(rollableResults: List<ProcessingOutput?>): List<ItemStack?> {
            val repairedRecord = this.getResultItem(net.minecraft.core.RegistryAccess.EMPTY)
            return mutableListOf(repairedRecord)
        }

        override fun getType(): RecipeType<*> = ModRecipeTypes.RECORD_PARTIAL_MATERIAL_REPAIR.get()
    }
}
