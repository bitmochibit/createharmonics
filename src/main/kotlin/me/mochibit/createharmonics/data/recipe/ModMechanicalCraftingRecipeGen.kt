package me.mochibit.createharmonics.data.recipe

import com.simibubi.create.AllItems
import com.simibubi.create.api.data.recipe.BaseRecipeProvider
import com.simibubi.create.api.data.recipe.MechanicalCraftingRecipeGen
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.registry.ModBlocks
import net.minecraft.data.PackOutput
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.Ingredient

class ModMechanicalCraftingRecipeGen(
    output: PackOutput,
) : MechanicalCraftingRecipeGen(output, CreateHarmonicsMod.Companion.MOD_ID) {
    val recordPressBaseRecipe =
        create { ModBlocks.RECORD_PRESS_BASE }.returns(1).recipe { b ->
            b.apply {
                key('P', Ingredient.of(AllItems.PRECISION_MECHANISM))
                key('A', Ingredient.of(AllItems.ANDESITE_ALLOY))
                key('E', Ingredient.of(AllItems.ELECTRON_TUBE))
                key('B', Ingredient.of(AllItems.BRASS_INGOT))
                key('X', Ingredient.of(Items.AMETHYST_SHARD))

                patternLine("AAA")
                patternLine("EXE")
                patternLine("BPB")
                patternLine("ABA")
                disallowMirrored()
            }
        }
}
