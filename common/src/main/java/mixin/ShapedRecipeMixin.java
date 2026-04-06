package mixin;

import me.mochibit.createharmonics.event.crafting.RecipeAssembledEvent;
import me.mochibit.createharmonics.foundation.eventbus.EventBus;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collections;

@Mixin(ShapedRecipe.class)
public abstract class ShapedRecipeMixin {
    @Inject(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"), remap = false)
    private void onRecipeAssembled(CraftingInput input, HolderLookup.Provider registries, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        if (result == null) return;

        if (!result.isEmpty()) {
            var ingredients = new ArrayList<ItemStack>();
            for (int i = 0; i < input.size(); i++) {
                ingredients.add(input.getItem(i));
            }
            EventBus.INSTANCE.post(new RecipeAssembledEvent(ingredients, Collections.singletonList(result)));
        }
    }

}

