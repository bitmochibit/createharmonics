package me.mochibit.createharmonics.foundation.registry

import dev.engine_room.flywheel.lib.model.baked.PartialModel
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.foundation.extension.asResource
import me.mochibit.createharmonics.foundation.info
import java.util.EnumMap
import kotlin.collections.set

object ModPartialModels : Registrable {
    private val recordModels =
        EnumMap<RecordType, PartialModel>(RecordType::class.java).apply {
            for (type in RecordType.entries) {
                this[type] = block("ethereal_record_visual/${type.name.lowercase()}")
            }
        }

    fun getRecordModel(type: RecordType): PartialModel = recordModels.getValue(type)

    private fun block(path: String): PartialModel = PartialModel.of("block/$path".asResource())

    private fun entity(path: String): PartialModel = PartialModel.of("entity/$path".asResource())

    override fun register() {
        "Lazily loading partial models".info()
    }
}
