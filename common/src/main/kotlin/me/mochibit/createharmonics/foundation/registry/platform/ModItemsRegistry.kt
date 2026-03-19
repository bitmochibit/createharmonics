package me.mochibit.createharmonics.foundation.registry.platform

import me.mochibit.createharmonics.content.records.RecordType
import net.minecraft.world.item.Item
import java.util.EnumMap

abstract class ModItemsRegistry<RegistryObjectType> : CrossPlatformRegistry<RegistryObjectType, Item> {
    override val referenceMap: MutableMap<Item, RegistryObjectType> = mutableMapOf()

    abstract val etherealRecordBase: Item
    abstract val etherealRecords: EnumMap<RecordType, Item>

    override fun registerEntry(name: String): CrossPlatformRegistry.ConvertibleEntry<RegistryObjectType, Item> =
        throw UnsupportedOperationException("You must use registrate for making entries of items!")
}
