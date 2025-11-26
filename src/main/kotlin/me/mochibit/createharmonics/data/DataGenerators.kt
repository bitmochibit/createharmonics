package me.mochibit.createharmonics.data


import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.data.recipe.ModRecipeProvider
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
        val lookUpProvider = event.lookupProvider
        val existingFileHelper = event.existingFileHelper


        if (event.includeClient()) {
            generator.addProvider(
                true,
                EtherealRecordVisualModelProvider(output, existingFileHelper)
            )
        }

        if (event.includeServer()) {
            ModRecipeProvider.registerAllProcessRecipes(generator, output, lookUpProvider)
        }
    }
}

