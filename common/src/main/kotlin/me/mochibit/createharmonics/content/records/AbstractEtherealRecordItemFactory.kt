package me.mochibit.createharmonics.content.records

import net.minecraft.world.item.Item

interface AbstractEtherealRecordItemFactory {
    fun create(
        recordType: RecordType,
        props: Item.Properties,
        brokenVariant: Boolean,
    ): EtherealRecordItem
}
