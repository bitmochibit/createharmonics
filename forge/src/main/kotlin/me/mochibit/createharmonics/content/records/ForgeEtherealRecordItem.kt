package me.mochibit.createharmonics.content.records

import net.minecraft.world.item.ItemStack

class ForgeEtherealRecordItem(
    recordType: RecordType,
    props: Properties,
    brokenVariant: Boolean,
) : EtherealRecordItem(recordType, props, brokenVariant) {
    override fun getMaxDamage(stack: ItemStack): Int = if (recordType.uses > 0) recordType.uses + 1 else 0
}
