package me.mochibit.createharmonics.content.records

import net.minecraft.world.item.Item

object ForgeEtherealRecordItemFactory : AbstractEtherealRecordItemFactory {
    override fun create(
        recordType: RecordType,
        props: Item.Properties,
        brokenVariant: Boolean,
    ): EtherealRecordItem = ForgeEtherealRecordItem(recordType, props, brokenVariant)
}
