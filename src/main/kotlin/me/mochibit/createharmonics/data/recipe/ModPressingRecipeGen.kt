package me.mochibit.createharmonics.data.recipe

import com.simibubi.create.api.data.recipe.PressingRecipeGen
import com.simibubi.create.content.kinetics.press.PressingRecipe
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.registry.ModItems
import net.minecraft.data.PackOutput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.Ingredient

class ModPressingRecipeGen(
    output: PackOutput,
) : PressingRecipeGen(output, CreateHarmonicsMod.Companion.MOD_ID) {
    val discPressingRecipes: List<GeneratedRecipe> =
        RecordType.entries.map {
            // Simply create a recipe that returns itself, useful for the record stamping base
            create<PressingRecipe>("ethereal_record/${it.name.lowercase()}") { builder ->
                builder
                    .require { ModItems.getEtherealRecordItem(it).get() }
                    .output(0.3f) { ItemStack(Items.AMETHYST_SHARD, 2).item }
                    .output(1f) { ItemStack(Items.AMETHYST_SHARD, 1).item }
            }
        }
}
