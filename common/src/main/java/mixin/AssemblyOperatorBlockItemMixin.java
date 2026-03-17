package mixin;

import com.simibubi.create.content.processing.AssemblyOperatorBlockItem;
import me.mochibit.createharmonics.foundation.services.ContentServiceKt;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AssemblyOperatorBlockItem.class)
public abstract class AssemblyOperatorBlockItemMixin {
    @Inject(
            method = "operatesOn",
            at = @At("HEAD"),
            remap = false,
            cancellable = true)
    private void onRecipeApply(LevelReader world, BlockPos pos, BlockState placedOnState, CallbackInfoReturnable<Boolean> cir) {
        var registry = ContentServiceKt.getContentService().getModBlocksRegistry();
        if (registry.getRecordPressBase().equals(placedOnState.getBlock())) {
            cir.setReturnValue(true);
        }
    }

}
