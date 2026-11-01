package me.mochibit.createharmonics.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import me.mochibit.createharmonics.config.client.ByteSizeEntry;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.config.ui.SubMenuConfigScreen;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SubMenuConfigScreen.class, remap = false)
public abstract class SubMenuConfigScreenMixin {

    @WrapOperation(
            method = "/lambda\\$init\\$\\d+/",
            at = @At(value = "NEW",
                    target = "(Ljava/lang/String;)Lnet/createmod/catnip/config/ui/ConfigScreenList$LabeledEntry;")
    )
    @SuppressWarnings("unchecked")
    private ConfigScreenList.LabeledEntry createharmonics$customEntries(
            String fallbackLabel,
            Operation<ConfigScreenList.LabeledEntry> original,
            @Local(name = "humanKey") String humanKey,
            @Local(name = "configValue") ModConfigSpec.ConfigValue<?> configValue,
            @Local(name = "valueSpec") ModConfigSpec.ValueSpec valueSpec) {

        if (ByteSizeEntry.accepts(configValue, valueSpec)) {
            return new ByteSizeEntry(humanKey, (ModConfigSpec.ConfigValue<Long>) configValue, valueSpec);
        }
        return original.call(fallbackLabel);
    }
}