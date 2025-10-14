package me.mochibit.createharmonics

import com.simibubi.create.foundation.data.CreateRegistrate
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.audio.provider.FFMPEG
import me.mochibit.createharmonics.audio.provider.YTDL
import me.mochibit.createharmonics.registry.ModCreativeTabs
import me.mochibit.createharmonics.registry.ModItemsRegistry
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.server.ServerStartingEvent
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.eventbus.api.SubscribeEvent
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
    }

    constructor(context: FMLJavaModLoadingContext) {
        val forgeModEventBus: IEventBus = context.modEventBus
        cRegistrate().registerEventListeners(forgeModEventBus)
        forgeModEventBus.addListener(::commonSetup)
        MinecraftForge.EVENT_BUS.register(this)
        ModItemsRegistry.register(forgeModEventBus)
        ModCreativeTabs.register(forgeModEventBus)
        YTDL.install()
        FFMPEG.install()
        registerConfig(ModConfig.Type.COMMON, Config.SPEC)
    }

    private val _registrate: CreateRegistrate = CreateRegistrate.create(MOD_ID)
    fun getRegistrate(): CreateRegistrate = _registrate

    private fun commonSetup(event: FMLCommonSetupEvent) {
        // Register network packets for audio streaming
        info("Create: Harmonics is setting up!")
    }


    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    fun onServerStarting(event: ServerStartingEvent) {
        info("Create: Harmonics server is starting!")
    }


    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
    object ClientModEvents {
        @SubscribeEvent
        fun onClientSetup(event: FMLClientSetupEvent) {
            info("Create: Harmonics client is setting up!")
        }
    }
}

val CreateHarmonics: CreateHarmonicsMod
    get() = CreateHarmonicsMod.instance

internal fun cRegistrate() = CreateHarmonics.getRegistrate()
