package mixin;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerTrait;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

@Mixin(ArmBlockEntity.class)
public class ArmBlockEntityMixin {
    @Shadow
    ArmBlockEntity.Phase phase;

    @Redirect(
            method = "checkForMusicAmong",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getOptionalValue(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/util/Optional;"
            )
    )
    private Optional<?> redirectHasRecord(BlockState state, Property<?> property) {
        Optional<?> original = state.getOptionalValue(property);

        if (state.hasProperty(RecordPlayerTrait.Companion.getHAS_ETHEREAL_RECORD())
                && state.getValue(RecordPlayerTrait.Companion.getHAS_ETHEREAL_RECORD())) {

            if (phase == ArmBlockEntity.Phase.DANCING) {
                return Optional.of(true);
            }

            return Optional.of(Math.random() < 0.01);
        }

        return original;
    }
}
