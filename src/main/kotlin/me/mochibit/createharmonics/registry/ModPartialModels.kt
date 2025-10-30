package me.mochibit.createharmonics.registry

import dev.engine_room.flywheel.lib.model.baked.PartialModel
import me.mochibit.createharmonics.CreateHarmonicsMod
import net.minecraft.resources.ResourceLocation

object ModPartialModels {

    val ETHEREAL_RECORD: PartialModel = block("ethereal_record_block")

    fun init() {
        ETHEREAL_RECORD.toString()
    }

    private fun block(path: String): PartialModel {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateHarmonicsMod.MOD_ID, "block/" + path))
    }

    private fun entity(path: String): PartialModel {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateHarmonicsMod.MOD_ID, "entity/" + path))
    }
}
