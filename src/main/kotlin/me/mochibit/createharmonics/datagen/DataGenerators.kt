package me.mochibit.createharmonics.datagen


import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger
import net.minecraftforge.data.event.GatherDataEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber(modid = CreateHarmonicsMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
object DataGenerators {
    @SubscribeEvent
    @JvmStatic
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

