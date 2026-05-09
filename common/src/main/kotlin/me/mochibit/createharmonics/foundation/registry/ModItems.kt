package me.mochibit.createharmonics.foundation.registry

import com.tterrag.registrate.util.entry.ItemEntry
import me.mochibit.createharmonics.ModRegistrate
import me.mochibit.createharmonics.content.records.BaseRecordItem
import me.mochibit.createharmonics.content.records.EtherealRecordItem
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.foundation.extension.asResource
import me.mochibit.createharmonics.foundation.info
import me.mochibit.createharmonics.foundation.services.PlatformService
import me.mochibit.createharmonics.foundation.services.platformService
import net.minecraft.client.renderer.item.ItemProperties
import net.minecraft.world.item.Item
import net.minecraft.world.item.Rarity
import net.neoforged.neoforge.client.model.generators.ModelFile
import java.util.EnumMap

object ModItems : CommonRegistry {
    override val registrationOrder = 3

    val BASE_RECORD: ItemEntry<BaseRecordItem> =
        ModRegistrate
            .item("ethereal_record_base") { BaseRecordItem(Item.Properties().stacksTo(16)) }
            .model { ctx, prov ->
                prov.generated(ctx, prov.modLoc("item/ethereal_record_base/base"))
            }.register()

    val BROKEN_ETHEREAL_RECORDS =
        EnumMap<RecordType, ItemEntry<EtherealRecordItem>>(RecordType::class.java).apply {
            RecordType.entries
                .filter { it != RecordType.CREATIVE }
                .forEach { this[it] = registerBrokenEtherealRecordVariant(it) }
        }

    val ETHEREAL_RECORDS =
        EnumMap<RecordType, ItemEntry<EtherealRecordItem>>(RecordType::class.java).apply {
            RecordType.entries.forEach { this[it] = registerEtherealRecordVariant(it) }
        }

    private fun registerBrokenEtherealRecordVariant(recordType: RecordType): ItemEntry<EtherealRecordItem> {
        val typeName = recordType.name.lowercase()
        return ModRegistrate
            .item("broken_${typeName}_ethereal_record") {
                EtherealRecordItem(recordType, Item.Properties().stacksTo(1), true)
            }.model { ctx, prov ->
                prov.generated(ctx, prov.modLoc("item/ethereal_record/${typeName}_broken"))
            }.register()
    }

    private fun registerEtherealRecordVariant(recordType: RecordType): ItemEntry<EtherealRecordItem> {
        val typeName = recordType.name.lowercase()
        return ModRegistrate
            .item("${typeName}_ethereal_record") {
                val properties = Item.Properties().stacksTo(1)
                if (recordType == RecordType.CREATIVE) properties.rarity(Rarity.EPIC)
                EtherealRecordItem(recordType, properties)
            }.model { ctx, prov ->
                // Niente più override — il modello è pulito
                prov.generated(ctx, prov.modLoc("item/ethereal_record/$typeName"))
            }.register()
    }

    fun getEtherealRecordItem(recordType: RecordType): ItemEntry<EtherealRecordItem> = ETHEREAL_RECORDS.getValue(recordType)

    fun getBrokenEtherealRecordItem(recordType: RecordType): ItemEntry<EtherealRecordItem>? = BROKEN_ETHEREAL_RECORDS[recordType]

    fun brokenVariantOf(recordType: RecordType): Item? = BROKEN_ETHEREAL_RECORDS[recordType]?.get()

    override fun register() {
        "Registering items".info()
    }

    infix fun ModItems.etherealRecord(recordType: RecordType): EtherealRecordItem = getEtherealRecordItem(recordType).get()
}
