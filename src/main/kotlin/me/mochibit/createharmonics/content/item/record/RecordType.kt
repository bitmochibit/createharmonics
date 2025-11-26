package me.mochibit.createharmonics.content.item.record

import com.simibubi.create.AllItems
import net.minecraft.world.item.crafting.Ingredient
import net.minecraftforge.common.Tags

enum class RecordType(val properties: Properties) {
    STONE(Properties(recordIngredientProvider = { Ingredient.of(Tags.Items.STONE) })),
    GOLD(Properties(recordIngredientProvider = { Ingredient.of(Tags.Items.INGOTS_GOLD) })),
    EMERALD(Properties(recordIngredientProvider = { Ingredient.of(Tags.Items.GEMS_EMERALD) })),
    DIAMOND(Properties(recordIngredientProvider = { Ingredient.of(Tags.Items.GEMS_DIAMOND) })),
    NETHERITE(Properties(recordIngredientProvider = { Ingredient.of(Tags.Items.INGOTS_NETHERITE) })),
    BRASS(Properties(recordIngredientProvider = { Ingredient.of(AllItems.BRASS_INGOT) }))
    ;

    data class Properties(
        val uses: Int = 100,
        val recordIngredientProvider: () -> Ingredient = { Ingredient.EMPTY },
    )

}