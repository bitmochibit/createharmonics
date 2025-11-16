package me.mochibit.createharmonics.registry

import com.tterrag.registrate.util.entry.ItemEntry
import me.mochibit.createharmonics.Config
import me.mochibit.createharmonics.Config.RecordType
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.cRegistrate
import me.mochibit.createharmonics.content.item.EtherealRecordItem
import net.minecraft.world.item.Item
import net.minecraftforge.eventbus.api.IEventBus
import java.util.*


object ModItemsRegistry : AbstractModRegistry {
    
    val BASE_RECORD: ItemEntry<Item> = cRegistrate()
        .item("ethereal_record_base") { Item(Item.Properties().stacksTo(16)) }
        .model { ctx, prov ->
            prov.generated(ctx, prov.mcLoc("item/ethereal_record_base"))
        }
        .register()

    val ETHEREAL_RECORDS = EnumMap<RecordType, ItemEntry<EtherealRecordItem>>(RecordType::class.java).apply {
        Config.recordVariants.forEach { (type, maxUses) ->
            val entry = registerEtherealRecordVariant(type, maxUses)
            this[type] = entry
        }
    }

    private fun registerEtherealRecordVariant(recordType: RecordType, maxUses: Int?): ItemEntry<EtherealRecordItem> {
        val name = "ethereal_record_${recordType.name.lowercase()}"
        return cRegistrate().item(name) {  
            val properties = Item.Properties().stacksTo(1)
            // If maxUses is not null, set durability. Otherwise, item is unbreakable.
            if (maxUses != null) {
                properties.durability(maxUses)
            }
            EtherealRecordItem(recordType, properties)
        }
            .model { ctx , prov ->
                prov.generated(ctx, prov.modLoc("item/$name"))
            }

            .register()
    }

    fun getEtherealRecordItem(recordType: RecordType): ItemEntry<EtherealRecordItem>? {
        return ETHEREAL_RECORDS[recordType]
    }

    override fun register(eventBus: IEventBus) {
        info("Registering items for ${CreateHarmonicsMod.MOD_ID}")
    }
}