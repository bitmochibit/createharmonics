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
        return ModRegistrate
            .item(name) {
                val properties = Item.Properties().stacksTo(1)
                if (recordType == RecordType.CREATIVE) {
                    properties.rarity(Rarity.EPIC)
                }
                EtherealRecordItem(recordType, properties)
            }.model { ctx, prov ->
                val builder = prov.generated(ctx, prov.modLoc("item/ethereal_record/$typeName"))

                if (recordType != RecordType.CREATIVE) {
                    builder
                        .override()
                        .predicate("broken".asResource(), 1f)
                        .model(
                            prov
                                .getBuilder("${ctx.name}_broken")
                                .parent(ModelFile.UncheckedModelFile("item/generated"))
                                .texture("layer0", prov.modLoc("item/ethereal_record/${typeName}_broken")),
                        ).end()
                }
            }.onRegister { item ->
                if (recordType == RecordType.CREATIVE) return@onRegister
                val platform = platformService
                if (platform.environment == PlatformService.Environment.CLIENT) {
                    ItemProperties.register(item, "broken".asResource()) { stack, _, _, _ ->
                        if (item.isRecordBroken(stack)) 1.0f else 0.0f
                    }
                }
            }.register()
    }

    fun getEtherealRecordItem(recordType: RecordType): ItemEntry<EtherealRecordItem> = ETHEREAL_RECORDS.getValue(recordType)

    override fun register() {
        "Registering items".info()
    }

    infix fun ModItems.etherealRecord(recordType: RecordType): EtherealRecordItem = getEtherealRecordItem(recordType).get()
}
