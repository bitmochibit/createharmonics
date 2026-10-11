package me.mochibit.createharmonics.data.record

import com.simibubi.create.AllItems
import me.mochibit.createharmonics.content.records.RecordRepairRecipe
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.content.records.RepairKind
import me.mochibit.createharmonics.foundation.extension.Tags
import me.mochibit.createharmonics.foundation.extension.asResource
import me.mochibit.createharmonics.foundation.extension.withPath
import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import net.minecraft.data.recipes.RecipeOutput
import net.minecraft.data.recipes.RecipeProvider
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.Ingredient
import java.util.concurrent.CompletableFuture

class RecordRecipeProvider(out: PackOutput, lookup: CompletableFuture<HolderLookup.Provider>) : RecipeProvider(out, lookup) {
    override fun buildRecipes(out: RecipeOutput) {
        val all = RecordType.entries.toSet()
        fun repair(name: String, types: Set<RecordType>, material: Ingredient, kind: RepairKind, fraction: Float = 1f) =
            out.accept("repair/$name".asResource(), RecordRepairRecipe(types, material, kind, fraction), null)

        repair("glue", all, Ingredient.of(AllItems.SUPER_GLUE.get()), RepairKind.GLUE)
        repair("stone", setOf(RecordType.STONE), Ingredient.of(Tags.Items withPath "stones"), RepairKind.FULL)
        repair("gold", setOf(RecordType.GOLD), Ingredient.of(Tags.Items withPath "ingots/gold"), RepairKind.FULL)
        repair("emerald", setOf(RecordType.EMERALD), Ingredient.of(Tags.Items withPath "gems/emerald"), RepairKind.FULL)
        repair("diamond", setOf(RecordType.DIAMOND), Ingredient.of(Tags.Items withPath "gems/diamond"), RepairKind.FULL)
        repair("netherite", setOf(RecordType.NETHERITE), Ingredient.of(Tags.Items withPath "ingots/netherite"), RepairKind.FULL)
        repair("netherite_scrap", setOf(RecordType.NETHERITE), Ingredient.of(Items.NETHERITE_SCRAP), RepairKind.PARTIAL, 0.25f)
        repair("brass", setOf(RecordType.BRASS), Ingredient.of(AllItems.BRASS_INGOT), RepairKind.FULL)
    }
}