package mixin;

import com.simibubi.create.content.processing.AssemblyOperatorBlockItem;
import me.mochibit.createharmonics.event.crafting.RecipeAssembledEvent;
import me.mochibit.createharmonics.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mixin(AssemblyOperatorBlockItem.class)
public abstract class AssemblyOperatorBlockItemMixin {
    @Inject(
            method = "operatesOn",
            at = @At("HEAD"),
            remap = false,
            cancellable = true)
    private void onRecipeApply(LevelReader world, BlockPos pos, BlockState placedOnState, CallbackInfoReturnable<Boolean> cir) {
        if (ModBlocks.INSTANCE.getRECORD_PRESS_BASE().has(placedOnState)) {
            cir.setReturnValue(true);
        }
    }

}
