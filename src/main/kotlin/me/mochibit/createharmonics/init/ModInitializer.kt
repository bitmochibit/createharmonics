package me.mochibit.createharmonics.init

import com.simibubi.create.foundation.data.CreateRegistrate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import me.mochibit.createharmonics.Config
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.audio.process.ProcessLifecycleManager
import me.mochibit.createharmonics.coroutine.launchModCoroutine
import me.mochibit.createharmonics.network.ModNetworkHandler
import me.mochibit.createharmonics.registry.ModPartialModels
import me.mochibit.createharmonics.registry.RegistryManager
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.DistExecutor
import net.minecraftforge.fml.config.ModConfig
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import thedarkcolour.kotlinforforge.forge.registerConfig

/**
 * Handles all mod initialization logic, including registry setup, event bus registration,
 * and lifecycle management.
 */
class ModInitializer(
    private val registrate: CreateRegistrate,
    private val forgeEventBus: IEventBus,
    private val modEventBus: IEventBus
) {

    fun initialize() {
        initializeClientSide()

        registrate.registerEventListeners(modEventBus)

        modEventBus.addListener(this::onCommonSetup)

        forgeEventBus.register(this)

        RegistryManager.registerAll(modEventBus)

        ModNetworkHandler.register(modEventBus)

        registerConfig(ModConfig.Type.COMMON, Config.SPEC)

        setupShutdownHooks()

        info("Create: Harmonics initialization complete!")
    }

    private fun initializeClientSide() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT) {
            Runnable {
                ModPartialModels.register(modEventBus)
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

