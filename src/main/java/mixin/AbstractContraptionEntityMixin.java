package mixin;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import me.mochibit.createharmonics.event.contraption.ContraptionDisassembleEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;


@Mixin(AbstractContraptionEntity.class)
public abstract class AbstractContraptionEntityMixin {
    @Inject(method = "disassemble", at = @At("HEAD"), remap = false)
    private void onDisassemble(CallbackInfo ci) {
        AbstractContraptionEntity entity = (AbstractContraptionEntity) (Object) this;

        var blockEntityDataMap = new HashMap<BlockPos, CompoundTag>();

        for (var actor : entity.getContraption().getActors()) {
            MovementContext ctx = actor.getRight();
            if (ctx.blockEntityData != null && !ctx.blockEntityData.isEmpty()) {
                BlockPos pos = actor.getLeft().pos();
                // Deep copy del CompoundTag
                blockEntityDataMap.put(pos, ctx.blockEntityData.copy());
            }
        }

        MinecraftForge.EVENT_BUS.post(new ContraptionDisassembleEvent(
                entity.getId(),
                entity.level(),
                blockEntityDataMap
        ));
    }
}