package me.mochibit.createharmonics.registry

import com.tterrag.registrate.util.entry.ItemEntry
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.cRegistrate
import me.mochibit.createharmonics.content.records.BaseRecordItem
import me.mochibit.createharmonics.content.records.EtherealRecordItem
import me.mochibit.createharmonics.content.records.RecordType
import net.minecraft.world.item.Item
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import java.util.EnumMap

object ModItems : AutoRegistrable {
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
                val recordProperties = recordType.properties
                if (recordProperties.uses > 0) {
                    properties.durability(recordProperties.uses + 1)
                }
                EtherealRecordItem(recordType, properties)
            }.model { ctx, prov ->
                prov.generated(ctx, prov.modLoc("item/ethereal_record/$typeName"))
            }.register()
    }

    fun getEtherealRecordItem(recordType: RecordType): ItemEntry<EtherealRecordItem> = ETHEREAL_RECORDS.getValue(recordType)

    override fun register(
        eventBus: IEventBus,
        context: FMLJavaModLoadingContext,
    ) {
        info("Registering items for ${CreateHarmonicsMod.MOD_ID}")
    }
}
