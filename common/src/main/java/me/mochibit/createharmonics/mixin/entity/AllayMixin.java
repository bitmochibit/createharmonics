package me.mochibit.createharmonics.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerHooks;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Allay.class)
public abstract class AllayMixin {
    @WrapOperation(
            method = "shouldStopDancing",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z")
    )
    private boolean createharmonics$jukeboxLike(BlockState state, Block block, Operation<Boolean> original) {
        return original.call(state, block) || RecordPlayerHooks.isPlayingRecordPlayer(state);
    }
}