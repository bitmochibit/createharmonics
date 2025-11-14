package me.mochibit.createharmonics

import com.simibubi.create.foundation.data.CreateRegistrate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import me.mochibit.createharmonics.CreateHarmonicsMod.Companion.MOD_ID
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.audio.process.ProcessLifecycleManager
import me.mochibit.createharmonics.client.event.MainMenuDisclaimerHandler
import me.mochibit.createharmonics.coroutine.launchModCoroutine
import me.mochibit.createharmonics.network.ModNetworkHandler
import me.mochibit.createharmonics.registry.*
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.server.ServerStartingEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.DistExecutor
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.Mod.EventBusSubscriber
import net.minecraftforge.fml.config.ModConfig
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import thedarkcolour.kotlinforforge.forge.registerConfig

@Mod(CreateHarmonicsMod.MOD_ID)
class CreateHarmonicsMod {
    companion object {
        const val MOD_ID: String = "createharmonics"

        @JvmStatic
        lateinit var instance: CreateHarmonicsMod
            private set
    }

    init {
        instance = this
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT) {
            Runnable {
                ModPartialModels.init()
            }
        }

        // Register shutdown hook to clean up processes when Minecraft exits
        Runtime.getRuntime().addShutdownHook(Thread {
            info("Minecraft shutting down, cleaning up managed processes...")
            ProcessLifecycleManager.shutdownAll()
        })
    }

    constructor(context: FMLJavaModLoadingContext) {
        val forgeModEventBus: IEventBus = context.modEventBus
        cRegistrate().registerEventListeners(forgeModEventBus)
        forgeModEventBus.addListener(::commonSetup)
        MinecraftForge.EVENT_BUS.register(this)
        ModBlocksRegistry.register(forgeModEventBus)
        ModBlockEntitiesRegistry.register(forgeModEventBus)
        ModItemsRegistry.register(forgeModEventBus)
        ModCreativeTabs.register(forgeModEventBus)
        ModMenuTypesRegistry.register(forgeModEventBus)
        ModArmInteractionPointRegistry.register(forgeModEventBus)
        ModNetworkHandler.register()
        registerConfig(ModConfig.Type.COMMON, Config.SPEC)
    }

    private val _registrate: CreateRegistrate = CreateRegistrate.create(MOD_ID)
    fun getRegistrate(): CreateRegistrate = _registrate

    private fun commonSetup(event: FMLCommonSetupEvent) {
        launchModCoroutine(Dispatchers.IO, isWorldSpecific = false) {
            commonSetupCoroutine(event)
        }
        info("Create: Harmonics is setting up!")
    }

    private suspend fun commonSetupCoroutine(event: FMLCommonSetupEvent) = coroutineScope {

    }


    @SubscribeEvent
    fun onServerStarting(event: ServerStartingEvent) {
        info("Create: Harmonics server is starting!")
    }

    @SubscribeEvent
    fun onServerStopping(event: ServerStoppingEvent) {
        info("Create: Harmonics server is stopping, cleaning up processes...")
        ProcessLifecycleManager.shutdownAll()
    }

    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.FORGE, value = [Dist.CLIENT])
    object ClientForgeEvents {
        @SubscribeEvent
        fun onClientDisconnect(event: net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut) {
            info("Client disconnecting from world, cleaning up processes...")
            ProcessLifecycleManager.shutdownAll()
        }
    }

    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
    object ClientModEvents {
        @SubscribeEvent
        fun onClientSetup(event: FMLClientSetupEvent) {
            info("Create: Harmonics client is setting up!")
            MinecraftForge.EVENT_BUS.register(MainMenuDisclaimerHandler)
        }
    }

}

val CreateHarmonics: CreateHarmonicsMod
    get() = CreateHarmonicsMod.instance

internal fun String.asResource() = ResourceLocation.fromNamespaceAndPath(MOD_ID, this)
internal fun cRegistrate() = CreateHarmonics.getRegistrate()
