package mixin;

import com.simibubi.create.content.processing.AssemblyOperatorBlockItem;
import me.mochibit.createharmonics.foundation.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AssemblyOperatorBlockItem.class)
public abstract class AssemblyOperatorBlockItemMixin {
    @Inject(method = "operatesOn", at = @At("HEAD"), cancellable = true, remap = false)
    private void onRecipeApply(LevelReader world, BlockPos pos, BlockState placedOnState, CallbackInfoReturnable<Boolean> cir) {
        var registry = ModBlocks.INSTANCE;
        if (registry.getRECORD_PRESS_BASE().has(placedOnState)) {
            cir.setReturnValue(true);
        }
    }

}


