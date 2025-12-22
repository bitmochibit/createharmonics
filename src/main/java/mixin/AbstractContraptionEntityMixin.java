package mixin;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import me.mochibit.createharmonics.event.contraption.ContraptionDisassembleEvent;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContraptionEntity.class)
public abstract class AbstractContraptionEntityMixin {
    @Inject(method = "disassemble", at = @At("TAIL"), remap = false)
    private void onDisassemble(CallbackInfo ci) {

        AbstractContraptionEntity entity = (AbstractContraptionEntity) (Object) this;

        MinecraftForge.EVENT_BUS.post(new ContraptionDisassembleEvent(entity.getId()));
    }
}
