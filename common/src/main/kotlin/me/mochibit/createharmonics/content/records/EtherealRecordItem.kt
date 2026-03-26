package me.mochibit.createharmonics.content.records

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.RecordItem

class EtherealRecordItem(
    val recordType: RecordType,
    props: Properties,
) : Item(
        props.apply {
            val maxDamage =
                if (recordType.uses > 0) {
                    recordType.uses + 1
                } else {
                    0
                }
            this.defaultDurability(maxDamage)
        },
    ) {
    override fun canBeDepleted(): Boolean = recordType.uses > 0

    override fun getDefaultInstance(): ItemStack {
        val discs =
            BuiltInRegistries.ITEM
                .stream()
                .filter { item -> item is RecordItem }
                .toList()

        val default = super.getDefaultInstance()
        RecordCraftingHandler.setCraftedWithDisc(default, ItemStack(discs.random()))
        return default
    }
}
