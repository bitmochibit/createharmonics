package me.mochibit.createharmonics.mixin.content.record;

import me.mochibit.createharmonics.content.records.EtherealRecordItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeItem;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EtherealRecordItem.class)
public abstract class EtherealRecordItemMixin implements IForgeItem {
    @Override
    public int getMaxDamage(ItemStack stack) {
        EtherealRecordItem recordItem = (EtherealRecordItem) (Object) this;
        var uses = recordItem.getRecordType().getUses();
        if (uses > 0)
            return uses + 1;
        return 0;
    }
}
