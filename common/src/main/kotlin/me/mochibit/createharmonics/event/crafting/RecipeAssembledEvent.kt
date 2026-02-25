package me.mochibit.createharmonics.event.crafting

import dev.architectury.event.Event
import dev.architectury.event.EventFactory
import net.minecraft.world.item.ItemStack

object RecipeAssembledEvent {
    @JvmField
    val EVENT: Event<Listener> = EventFactory.createLoop()

    fun interface Listener {
        fun onRecipeAssembled(
            ingredients: List<ItemStack>,
            result: List<ItemStack>,
        )
    }
}
