package me.mochibit.createharmonics.data

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.tterrag.registrate.providers.ProviderType
import me.mochibit.createharmonics.CreateHarmonicsMod.MOD_ID
import me.mochibit.createharmonics.ModRegistrate
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.data.recipe.ModRecipeProvider
import me.mochibit.createharmonics.foundation.err
import me.mochibit.createharmonics.foundation.info
import me.mochibit.createharmonics.ponder.ModPonderPlugin
import net.createmod.ponder.foundation.PonderIndex
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.data.event.GatherDataEvent
import java.io.BufferedReader
import java.io.InputStreamReader

@EventBusSubscriber(modid = MOD_ID)
object DataGenerators {
    @SubscribeEvent
    @JvmStatic
    fun onGatherData(event: GatherDataEvent) {
        "Generating data for Create: Harmonics".info()
        val generator = event.generator
        val output = generator.packOutput
        val lookUpProvider = event.lookupProvider
        val existingFileHelper = event.existingFileHelper

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
        ModRegistrate.addDataGenerator(ProviderType.LANG) { provider ->
            val langConsumer: (String, String) -> Unit = { key, value ->
                provider.add(key, value)
            }

            RecordType.Properties.EffectAttribute.provideLang(langConsumer)

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

        PonderIndex.getLangAccess().provideLang(MOD_ID, consumer)
    }
}

object JsonResourceLoader {
    private val gson = Gson()

    fun loadJsonResource(path: String): JsonElement? {
        return try {
            val inputStream = JsonResourceLoader::class.java.classLoader.getResourceAsStream(path)
            if (inputStream == null) {
                "Could not find resource: $path".err()
                return null
            }

            val reader = BufferedReader(InputStreamReader(inputStream))
            val json = gson.fromJson(reader, JsonElement::class.java)
            reader.close()
            json
        } catch (e: Exception) {
            "Error loading JSON resource $path: ${e.message}".err()
            null
        }
    }
}
