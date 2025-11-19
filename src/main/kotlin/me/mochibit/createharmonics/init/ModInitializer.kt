package me.mochibit.createharmonics.init

import com.simibubi.create.foundation.data.CreateRegistrate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.audio.process.ProcessLifecycleManager
import me.mochibit.createharmonics.coroutine.launchModCoroutine
import me.mochibit.createharmonics.datagen.DataGenerators
import me.mochibit.createharmonics.network.ModNetworkHandler
import me.mochibit.createharmonics.registry.ModConfigRegistry
import me.mochibit.createharmonics.registry.ModPartialModels
import me.mochibit.createharmonics.registry.RegistryManager
import net.createmod.catnip.config.ui.BaseConfigScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory
import net.minecraftforge.data.event.GatherDataEvent
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.DistExecutor
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext

/**
 * Handles all mod initialization logic, including registry setup, event bus registration,
 * and lifecycle management.
 */
class ModInitializer(
    private val registrate: CreateRegistrate,
    private val forgeEventBus: IEventBus,
    private val modEventBus: IEventBus,
    private val context: FMLJavaModLoadingContext
) {

    fun initialize() {
        initializeClientSide()

        registrate.registerEventListeners(modEventBus)

        modEventBus.addListener(this::onCommonSetup)
        modEventBus.addListener(this::onClientSetup)
        modEventBus.addListener(this::onLoadComplete)
        modEventBus.addListener { event: GatherDataEvent -> DataGenerators.onGatherData(event) }

        forgeEventBus.register(this)


        RegistryManager.registerAll(modEventBus, context)

        ModNetworkHandler.register(modEventBus, context)

        ModConfigRegistry.register(modEventBus, context)

        setupShutdownHooks()

        info("Create: Harmonics initialization complete!")
    }

    private fun initializeClientSide() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT) {
            Runnable {
                ModPartialModels.register(modEventBus, context)
            }
        }
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        BaseConfigScreen.setDefaultActionFor(CreateHarmonicsMod.MOD_ID) { base ->
            base.withSpecs(null, ModConfigRegistry.common.specification, null)
        }
    }

    fun onLoadComplete(event: FMLLoadCompleteEvent) {
        context.registerExtensionPoint(
            ConfigScreenFactory::class.java
        ) {
            ConfigScreenFactory { mc: Minecraft?, previousScreen: Screen? ->
                BaseConfigScreen(
                    previousScreen,
                    CreateHarmonicsMod.MOD_ID
                )
            }
        }
    }

    private fun onCommonSetup(event: FMLCommonSetupEvent) {
        launchModCoroutine(Dispatchers.IO) {
            commonSetupCoroutine(event)
        }
        info("Create: Harmonics common setup complete!")
    }


    private suspend fun commonSetupCoroutine(event: FMLCommonSetupEvent) = coroutineScope {
    }

    /**
     * Register shutdown hooks to clean up resources.
     */
    private fun setupShutdownHooks() {
        Runtime.getRuntime().addShutdownHook(Thread {
            info("Minecraft shutting down, cleaning up managed processes...")
            ProcessLifecycleManager.shutdownAll()
        })
    }
}

