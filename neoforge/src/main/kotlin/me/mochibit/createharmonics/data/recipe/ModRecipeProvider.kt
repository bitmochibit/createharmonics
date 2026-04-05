package me.mochibit.createharmonics.data.recipe

import net.minecraft.core.HolderLookup
import net.minecraft.data.CachedOutput
import net.minecraft.data.DataGenerator
import net.minecraft.data.DataProvider
import net.minecraft.data.PackOutput
import net.minecraft.data.recipes.RecipeOutput
import net.minecraft.data.recipes.RecipeProvider
import java.util.concurrent.CompletableFuture

class ModRecipeProvider(
    packOutput: PackOutput,
    registries: CompletableFuture<HolderLookup.Provider>,
) : RecipeProvider(packOutput, registries) {
    companion object {
        fun registerAllProcessRecipes(
            gen: DataGenerator,
            output: PackOutput,
            registries: CompletableFuture<HolderLookup.Provider>,
        ) {
            val generators =
                listOf(
                    ModDeployingRecipeGen(output, registries),
                    ModPressingRecipeGen(output, registries),
                    ModMechanicalCraftingRecipeGen(output, registries),
                )

            gen.addProvider(
                true,
                object : DataProvider {
                    override fun run(pOutput: CachedOutput): CompletableFuture<*> =
                        CompletableFuture.allOf(
                            *generators
                                .map { gen ->
                                    gen.run(pOutput)
                                }.toTypedArray(),
                        )

                    override fun getName(): String = "Create Harmonics Processing Recipes"
                },
            )
        }
    }

    override fun buildRecipes(
        p_recipeOutput: RecipeOutput,
        holderLookup: HolderLookup.Provider,
    ) {
    }
}
