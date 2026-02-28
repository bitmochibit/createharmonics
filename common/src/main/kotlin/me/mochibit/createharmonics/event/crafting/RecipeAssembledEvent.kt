package me.mochibit.createharmonics.event.crafting

import me.mochibit.createharmonics.foundation.eventbus.ModEvent
import net.minecraft.world.item.ItemStack

data class RecipeAssembledEvent(
    val ingredients: List<ItemStack>,
    val result: List<ItemStack>,
) : ModEvent
