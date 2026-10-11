package me.mochibit.createharmonics.content.records

import com.mojang.serialization.MapCodec
import com.simibubi.create.AllItems
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipeParams
import com.simibubi.create.content.processing.recipe.ProcessingOutput
import me.mochibit.createharmonics.foundation.extension.asResource
import me.mochibit.createharmonics.foundation.registry.ModItems
import me.mochibit.createharmonics.foundation.registry.ModRecipeTypes
import me.mochibit.createharmonics.handler.RecordRepairHandler
import net.minecraft.core.HolderLookup
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.neoforge.items.wrapper.EmptyItemHandler
import net.neoforged.neoforge.items.wrapper.RecipeWrapper

class DeployerRecordRepairRecipe(
    params: ItemApplicationRecipeParams,
    private val capturedInput: RecipeWrapper,
    private val data: RecordRepairRecipe,
    private val be: BlockEntity,
) : DeployerApplicationRecipe(params) {

    override fun matches(input: RecipeWrapper, level: Level): Boolean =
        data.matches(input.getItem(0), input.getItem(1))

    override fun getResultItem(registries: HolderLookup.Provider): ItemStack =
        data.repair(capturedInput.getItem(0))

    override fun assemble(input: RecipeWrapper, registries: HolderLookup.Provider): ItemStack =
        getResultItem(registries)

    override fun rollResults(
        rollableResults: List<ProcessingOutput?>,
        randomSource: RandomSource,
    ): List<ItemStack?> {
        val record = capturedInput.getItem(0)
        val result = data.repair(record)
        applySideEffects(record)
        return mutableListOf(result)
    }

    private fun applySideEffects(record: ItemStack) {
        when (data.kind) {
            RepairKind.GLUE -> consumeGlue(record)
            RepairKind.FULL, RepairKind.PARTIAL -> Unit
        }
    }

    private fun consumeGlue(record: ItemStack) {
        val item = record.item as? EtherealRecordItem ?: return
        val glue = capturedInput.getItem(1)

        val currentDamage = if (item.isRecordBroken()) record.maxDamage else record.damageValue
        val cost = RecordRepairRecipe.calculateGlueRepairCost(item.recordType.uses, currentDamage)

        val newDamage = glue.damageValue + cost
        if (newDamage >= glue.maxDamage) glue.shrink(1) else glue.damageValue = newDamage

        be.level?.playSound(null, be.blockPos, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 1f, 1f)
    }

    override fun getType(): RecipeType<*> = ModRecipeTypes.RECORD_REPAIR.get()
}