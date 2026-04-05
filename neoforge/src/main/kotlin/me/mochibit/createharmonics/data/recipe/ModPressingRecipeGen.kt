package me.mochibit.createharmonics.data.recipe

import com.simibubi.create.api.data.recipe.PressingRecipeGen
import me.mochibit.createharmonics.CreateHarmonicsMod.MOD_ID
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.foundation.registry.ModItems
import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.concurrent.CompletableFuture

class ModPressingRecipeGen(
    output: PackOutput,
    registries: CompletableFuture<HolderLookup.Provider>,
) : PressingRecipeGen(output, registries, MOD_ID) {
    val discPressingRecipes: List<GeneratedRecipe> =
        RecordType.entries.map {
            // Simply create a recipe that returns itself, useful for the record stamping base
            create("ethereal_record/${it.name.lowercase()}") { builder ->
                builder
                    .require { ModItems.getEtherealRecordItem(it).get() }
                    .output(0.3f) { ItemStack(Items.AMETHYST_SHARD, 2).item }
                    .output(1f) { ItemStack(Items.AMETHYST_SHARD, 1).item }
            }
        }
}
