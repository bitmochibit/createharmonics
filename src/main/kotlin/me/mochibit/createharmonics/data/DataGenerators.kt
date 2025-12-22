package me.mochibit.createharmonics.data

import com.tterrag.registrate.providers.ProviderType
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.cRegistrate
import me.mochibit.createharmonics.data.recipe.ModRecipeProvider
import me.mochibit.createharmonics.foundation.ponder.ModPonderPlugin
import net.createmod.ponder.foundation.PonderIndex
import net.minecraftforge.data.event.GatherDataEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import org.apache.logging.log4j.util.BiConsumer

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

        provideLang()

        if (event.includeClient()) {
            generator.addProvider(
                true,
                EtherealRecordVisualModelProvider(output, existingFileHelper),
            )
        }

        if (event.includeServer()) {
            ModRecipeProvider.registerAllProcessRecipes(generator, output, lookUpProvider)
        }
    }

    fun provideLang() {
        cRegistrate().addDataGenerator(ProviderType.LANG) { provider ->
            val langConsumer = { key: String, default: String ->
                provider.add(key, default)
            }

            PonderIndex.addPlugin(ModPonderPlugin())
            PonderIndex.getLangAccess().provideLang(CreateHarmonicsMod.MOD_ID, langConsumer)
        }
    }
}
