package me.mochibit.createharmonics.event.crafting

import net.minecraft.core.RegistryAccess
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Recipe
import net.minecraftforge.eventbus.api.Event

class RecipeAssembledEvent(
    val ingredients: List<ItemStack>,
    val result: List<ItemStack>,
) : Event()
