package mixin;

import com.simibubi.create.content.processing.AssemblyOperatorBlockItem;
import com.simibubi.create.foundation.recipe.RecipeApplier;
import me.mochibit.createharmonics.event.crafting.RecipeAssembledEvent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mixin(RecipeApplier.class)
public abstract class RecipeApplierMixin {
    @Inject(
            method = "applyRecipeOn(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/crafting/Recipe;Z)Ljava/util/List;",
            at = @At("RETURN"),
            remap = false
    )
    private static void onRecipeApply(Level level, ItemStack stackIn, Recipe<?> recipe, boolean returnProcessingRemainder, CallbackInfoReturnable<List<ItemStack>> cir) {
        List<ItemStack> result = cir.getReturnValue();
        if (result == null) return;
        if (!result.isEmpty()) {
            RecipeAssembledEvent event = new RecipeAssembledEvent(new ArrayList<>(Collections.singleton(stackIn)), result);
            MinecraftForge.EVENT_BUS.post(event);
        }
    }

    @Inject(
            method = "applyRecipeOn(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/crafting/Recipe;Z)V",
            at = @At("RETURN"),
            remap = false
    )
    private static void onRecipeApplyOverload(ItemEntity entity, Recipe<?> recipe, boolean returnProcessingRemainder, CallbackInfo ci) {
        ItemStack stackIn = entity.getItem();
        List<ItemStack> result = RecipeApplier.applyRecipeOn(entity.level(), stackIn, recipe, returnProcessingRemainder);
        if (result == null) return;
        if (!result.isEmpty()) {
            RecipeAssembledEvent event = new RecipeAssembledEvent(new ArrayList<>(Collections.singleton(stackIn)), result);
            MinecraftForge.EVENT_BUS.post(event);
        }
    }
}

