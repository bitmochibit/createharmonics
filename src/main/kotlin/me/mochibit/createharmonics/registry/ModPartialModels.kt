package me.mochibit.createharmonics.registry

import dev.engine_room.flywheel.lib.model.baked.PartialModel
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.content.item.record.RecordType
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import java.util.*

object ModPartialModels : AbstractModRegistry {
    private val recordModels = EnumMap<RecordType, PartialModel>(RecordType::class.java).apply {
        for (type in RecordType.entries) {
            this[type] = block("ethereal_record_visual/${type.name.lowercase()}")
        }
    }

    fun getRecordModel(type: RecordType): PartialModel {
        return recordModels.getValue(type)
    }

    private fun block(path: String): PartialModel {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateHarmonicsMod.MOD_ID, "block/$path"))
    }

    private fun entity(path: String): PartialModel {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateHarmonicsMod.MOD_ID, "entity/$path"))
    }

    override fun register(eventBus: IEventBus, context: FMLJavaModLoadingContext) {
        Logger.info("Registering partial models")
    }
}
