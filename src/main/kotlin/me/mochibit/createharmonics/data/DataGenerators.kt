package me.mochibit.createharmonics.data

import com.tterrag.registrate.providers.ProviderType
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.cRegistrate
import me.mochibit.createharmonics.data.recipe.ModRecipeProvider
import me.mochibit.createharmonics.foundation.ponder.ModPonderPlugin
import me.mochibit.createharmonics.foundation.utility.JsonResourceLoader
import net.createmod.ponder.foundation.PonderIndex
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
            val langConsumer: (String, String) -> Unit = { key, value ->
                provider.add(key, value)
            }

            provideDefaultLang(langConsumer)

            providePonderLang(langConsumer)
        }
    }

    private fun provideDefaultLang(consumer: (String, String) -> Unit) {
        val path = "assets/createharmonics/lang/default/en_us.json"
        val jsonElement =
            JsonResourceLoader.loadJsonResource(path)
                ?: throw IllegalStateException("Could not find default lang file: $path")
        val jsonObject = jsonElement.asJsonObject
        for (entry in jsonObject.entrySet()) {
            val key = entry.key
            val value = entry.value.asString
            consumer(key, value)
        }
    }

    private fun providePonderLang(consumer: (String, String) -> Unit) {
        // Register this since FMLClientSetupEvent does not run during datagen
        PonderIndex.addPlugin(ModPonderPlugin())

        PonderIndex.getLangAccess().provideLang(CreateHarmonicsMod.MOD_ID, consumer)
    }
}
