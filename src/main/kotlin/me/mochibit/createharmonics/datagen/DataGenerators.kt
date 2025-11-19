package me.mochibit.createharmonics.datagen


import me.mochibit.createharmonics.Logger
import net.minecraftforge.data.event.GatherDataEvent


object DataGenerators {
    fun onGatherData(event: GatherDataEvent) {
        Logger.info("Generating data for Create: Harmonics")
        val generator = event.generator
        val output = generator.packOutput
        val existingFileHelper = event.existingFileHelper

        // Register client-side data generators
        if (event.includeClient()) {
            // Register the ethereal record visual model provider
            generator.addProvider(
                true,
                EtherealRecordVisualModelProvider(output, existingFileHelper)
            )
        }
    }
}

