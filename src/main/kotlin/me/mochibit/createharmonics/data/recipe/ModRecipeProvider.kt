package me.mochibit.createharmonics.data.recipe

import net.minecraft.core.HolderLookup
import net.minecraft.data.CachedOutput
import net.minecraft.data.DataGenerator
import net.minecraft.data.DataProvider
import net.minecraft.data.PackOutput
import net.minecraft.data.recipes.FinishedRecipe
import net.minecraft.data.recipes.RecipeProvider
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class ModRecipeProvider(
    packOutput: PackOutput,
) : RecipeProvider(packOutput) {
    companion object {
        fun registerAllProcessRecipes(
            gen: DataGenerator,
            output: PackOutput,
            registries: CompletableFuture<HolderLookup.Provider>,
        ) {
            val generators =
                listOf(
                    ModDeployingRecipeGen(output),
                    ModPressingRecipeGen(output),
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

    override fun buildRecipes(pWriter: Consumer<FinishedRecipe?>) {
    }
}
