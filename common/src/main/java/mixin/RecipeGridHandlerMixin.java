package mixin;

import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import me.mochibit.createharmonics.event.crafting.RecipeAssembledEvent;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Collections;

@Mixin(RecipeGridHandler.class)
public abstract class RecipeGridHandlerMixin {
    @Inject(
            method = "tryToApplyRecipe",
            at = @At("RETURN"),
            locals = LocalCapture.CAPTURE_FAILSOFT,
            remap = false
    )
    private static void onRecipeAssembled(Level world, RecipeGridHandler.GroupedItems items, CallbackInfoReturnable<ItemStack> cir, CraftingContainer inv) {
        ItemStack result = cir.getReturnValue();
        if (result == null) return;

        if (!result.isEmpty()) {
            RecipeAssembledEvent.EVENT.invoker().onRecipeAssembled(inv.getItems(), Collections.singletonList(result));
        }
    }
}



