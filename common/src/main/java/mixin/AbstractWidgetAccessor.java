package mixin;

import org.spongepowered.asm.mixin.gen.Accessor;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.gui.components.AbstractWidget.class)
public interface AbstractWidgetAccessor {
    @Accessor
    void setHeight(int height);
}
