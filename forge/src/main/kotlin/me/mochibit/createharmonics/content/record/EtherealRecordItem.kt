package me.mochibit.createharmonics.content.record

import me.mochibit.createharmonics.content.records.RecordCraftingHandler
import me.mochibit.createharmonics.content.records.RecordType
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.RecordItem
import net.minecraftforge.registries.ForgeRegistries

class EtherealRecordItem(
    val recordType: RecordType,
    props: Properties,
) : Item(props) {
    override fun getMaxDamage(stack: ItemStack): Int {
        val uses = recordType.uses
        return if (uses > 0) uses + 1 else 0
    }

    override fun isDamageable(stack: ItemStack): Boolean = recordType.uses > 0

    override fun getDefaultInstance(): ItemStack {
        val discs =
            ForgeRegistries.ITEMS.values
                .stream()
                .filter { item -> item is RecordItem }
                .toList()

        val default = super.getDefaultInstance()
        RecordCraftingHandler.setCraftedWithDisc(default, ItemStack(discs.random()))
        return default
    }
}
