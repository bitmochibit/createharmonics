package me.mochibit.createharmonics

import com.simibubi.create.foundation.data.CreateRegistrate
import me.mochibit.createharmonics.CreateHarmonicsMod.Companion.MOD_ID
import me.mochibit.createharmonics.Logger.err
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.audio.AudioPlayerRegistry
import me.mochibit.createharmonics.audio.process.ProcessLifecycleManager
import me.mochibit.createharmonics.foundation.ponder.ModPonderPlugin
import me.mochibit.createharmonics.registry.ModConfigurations
import me.mochibit.createharmonics.registry.ModPackets
import me.mochibit.createharmonics.registry.ModPartialModels
import me.mochibit.createharmonics.registry.RegistryManager
import net.createmod.catnip.config.ui.BaseConfigScreen
import net.createmod.ponder.api.registration.PonderPlugin
import net.createmod.ponder.foundation.PonderIndex
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.DistExecutor
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext

@Mod(MOD_ID)
class CreateHarmonicsMod(
    val context: FMLJavaModLoadingContext,
) {
    companion object {
        const val MOD_ID: String = "createharmonics"

        @JvmStatic
        lateinit var instance: CreateHarmonicsMod
            private set
    }

    private val registrate: CreateRegistrate = CreateRegistrate.create(MOD_ID)

    init {
        instance = this
        initialize()
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    object ModSetup {
        @JvmStatic
        @SubscribeEvent
        fun onClientSetup(event: FMLClientSetupEvent) {
            PonderIndex.addPlugin(ModPonderPlugin())
            BaseConfigScreen.setDefaultActionFor(MOD_ID) { base ->
                base.withSpecs(null, ModConfigurations.common.specification, null)
            }
        }

        @JvmStatic
        @SubscribeEvent
        fun onLoadComplete(event: FMLLoadCompleteEvent) {
            instance.context.registerExtensionPoint(
                ConfigScreenFactory::class.java,
            ) {
                ConfigScreenFactory { mc: Minecraft, previousScreen: Screen ->
                    BaseConfigScreen(
                        previousScreen,
                        MOD_ID,
                    )
                }
            }
        }

        @JvmStatic
        @SubscribeEvent
        fun onCommonSetup(event: FMLCommonSetupEvent) {
            info("Create: Harmonics common setup complete!")
        }
    }

    private fun initialize() {
        val forgeEventBus = MinecraftForge.EVENT_BUS
        val modEventBus = context.modEventBus
        forgeEventBus.register(this)
        registrate.registerEventListeners(modEventBus)

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT) {
            Runnable {
                ModPartialModels.register(modEventBus, context)
            }
        }

        RegistryManager.registerAll(modEventBus, context)

        ModPackets.register(modEventBus, context)

        Runtime.getRuntime().addShutdownHook(
            Thread {
                info("Minecraft shutting down, cleaning up resources...")
                try {
                    AudioPlayerRegistry.clear()
                } catch (e: Exception) {
                    err("Error clearing streams: ${e.message}")
                }
                try {
                    // Then cleanup managed processes
                    ProcessLifecycleManager.shutdownAll()
                } catch (e: Exception) {
                    err("Error shutting down processes: ${e.message}")
                }
            },
        )
    }

    fun getRegistrate(): CreateRegistrate = registrate
}

val CreateHarmonics: CreateHarmonicsMod
    get() = CreateHarmonicsMod.instance

internal fun String.asResource() = ResourceLocation.fromNamespaceAndPath(MOD_ID, this)

internal fun cRegistrate() = CreateHarmonics.getRegistrate()
