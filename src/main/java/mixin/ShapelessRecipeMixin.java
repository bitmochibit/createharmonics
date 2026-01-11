package mixin;

import me.mochibit.createharmonics.event.crafting.RecipeAssembledEvent;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collections;

@Mixin(ShapelessRecipe.class)
public abstract class ShapelessRecipeMixin {
    @Inject(method = "assemble(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
    private void onRecipeAssembled(CraftingContainer container, RegistryAccess reg, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        if (!result.isEmpty()) {
            var ingredients = new ArrayList<ItemStack>();
            for (int i = 0; i < container.getContainerSize(); i++) {
                ingredients.add(container.getItem(i));
            }
            RecipeAssembledEvent event = new RecipeAssembledEvent(ingredients, Collections.singletonList(result));
            MinecraftForge.EVENT_BUS.post(event);
        }
    }
}
