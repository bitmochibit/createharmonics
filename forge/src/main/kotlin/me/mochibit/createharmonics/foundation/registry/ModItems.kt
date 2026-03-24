package me.mochibit.createharmonics.foundation.registry

import com.tterrag.registrate.util.entry.ItemEntry
import me.mochibit.createharmonics.cRegistrate
import me.mochibit.createharmonics.content.record.EtherealRecordItem
import me.mochibit.createharmonics.content.records.BaseRecordItem
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.foundation.info
import me.mochibit.createharmonics.foundation.registry.platform.asDelegate
import net.minecraft.world.item.Item
import net.minecraft.world.item.Rarity
import java.util.EnumMap
import kotlin.collections.set

object ModItems : ForgeRegistry, ModItemsRegistry<ItemEntry<*>>() {
    override val registrationOrder = 3

    val BASE_RECORD: ItemEntry<BaseRecordItem> =
        cRegistrate()
            .item("ethereal_record_base") { BaseRecordItem(Item.Properties().stacksTo(16)) }
            .model { ctx, prov ->
                prov.generated(ctx, prov.modLoc("item/ethereal_record_base/base"))
            }.register()

    val ETHEREAL_RECORDS =
        EnumMap<RecordType, ItemEntry<EtherealRecordItem>>(RecordType::class.java).apply {
            RecordType.entries.forEach { type ->
                val entry = registerEtherealRecordVariant(type)
                this[type] = entry
            }
        }

    private fun registerEtherealRecordVariant(recordType: RecordType): ItemEntry<EtherealRecordItem> {
        val typeName = recordType.name.lowercase()
        val name = "${typeName}_ethereal_record"
        return cRegistrate()
            .item(name) {
                val properties = Item.Properties().stacksTo(1)
                if (recordType == RecordType.CREATIVE) {
                    properties.rarity(Rarity.EPIC)
                }
                EtherealRecordItem(recordType, properties)
            }.model { ctx, prov ->
                prov.generated(ctx, prov.modLoc("item/ethereal_record/$typeName"))
            }.register()
    }

    fun getEtherealRecordItem(recordType: RecordType): ItemEntry<EtherealRecordItem> = ETHEREAL_RECORDS.getValue(recordType)

    override fun register() {
        "Registering items".info()
    }

    infix fun ModItems.etherealRecord(recordType: RecordType): EtherealRecordItem = getEtherealRecordItem(recordType).get()

    override val etherealRecordBase: Item by BASE_RECORD.asDelegate()

    override val etherealRecords: EnumMap<RecordType, Item> by lazy {
        EnumMap<RecordType, Item>(RecordType::class.java).also { map ->
            ETHEREAL_RECORDS.forEach { (k, v) -> map[k] = v.get() }
        }
    }
}
