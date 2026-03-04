package me.mochibit.createharmonics.content.record

import me.mochibit.createharmonics.content.records.RecordCraftingHandler
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.content.records.RecordUtilities.musicDiscs
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

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
        val default = super.getDefaultInstance()
        RecordCraftingHandler.setCraftedWithDisc(default, ItemStack(musicDiscs.random()))
        return default
    }
}
