package me.mochibit.createharmonics.datagen

import me.mochibit.createharmonics.CreateHarmonicsMod.Companion.MOD_ID
import me.mochibit.createharmonics.content.item.record.RecordType
import net.minecraft.data.PackOutput
import net.minecraftforge.client.model.generators.BlockModelProvider
import net.minecraftforge.common.data.ExistingFileHelper

/**
 * Data generator for ethereal record visual block models.
 * Generates model files for each RecordType variant with proper texture references.
 */
class EtherealRecordVisualModelProvider(
    output: PackOutput,
    existingFileHelper: ExistingFileHelper
) : BlockModelProvider(output, MOD_ID, existingFileHelper) {

    override fun registerModels() {
        // Iterate through all RecordType values and generate models
        RecordType.entries.forEach { recordType ->
            val modelName = recordType.name.lowercase()
            generateRecordVisualModel(modelName)
        }
    }

    /**
     * Generates a single model file for a given record type
     */
    private fun generateRecordVisualModel(modelName: String) {
        // Create a model with parent pointing to visual.json
        withExistingParent(
            "block/ethereal_record_visual/$modelName",
            modLoc("block/ethereal_record_visual/visual")
        ).texture("0", modLoc("block/ethereal_record_visual/$modelName"))
            .texture("particle", modLoc("block/ethereal_record_visual/$modelName"))
    }

    override fun getName(): String = "Ethereal Record Visual Models"
}

