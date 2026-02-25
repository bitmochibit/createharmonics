package me.mochibit.createharmonics

import com.simibubi.create.foundation.data.CreateRegistrate
import com.simibubi.create.foundation.item.ItemDescription
import com.simibubi.create.foundation.item.KineticStats
import com.simibubi.create.foundation.item.TooltipModifier
import me.mochibit.createharmonics.ForgeCreateHarmonicsMod.Companion.MOD_ID
import me.mochibit.createharmonics.audio.AudioPlayerRegistry
import me.mochibit.createharmonics.audio.process.ProcessLifecycleManager
import me.mochibit.createharmonics.foundation.err
import me.mochibit.createharmonics.foundation.registry.ForgeModPackets
import me.mochibit.createharmonics.foundation.registry.ModConfigurations
import me.mochibit.createharmonics.foundation.registry.RegistryManager
import me.mochibit.createharmonics.ponder.ModPonderPlugin
import net.createmod.catnip.config.ui.BaseConfigScreen
import net.createmod.catnip.lang.FontHelper
import net.createmod.ponder.foundation.PonderIndex
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.DistExecutor
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext

@Mod(MOD_ID)
class ForgeCreateHarmonicsMod(
    val context: FMLJavaModLoadingContext,
) {
    companion object {
        const val MOD_ID: String = "createharmonics"

        @JvmStatic
        lateinit var instance: ForgeCreateHarmonicsMod
            private set
    }

    private val registrate: CreateRegistrate =
        CreateRegistrate.create(MOD_ID).setTooltipModifierFactory { item ->
            ItemDescription
                .Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                .andThen(TooltipModifier.mapNull(KineticStats.create(item)))
        }

    init {
        instance = this
        initialize()
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    object ModSetup {
        @JvmStatic
        @SubscribeEvent
        fun onClientSetup(event: FMLClientSetupEvent) {
            BaseConfigScreen.setDefaultActionFor(MOD_ID) { base ->
                base.withSpecs(
                    ModConfigurations.client.specification,
                    ModConfigurations.common.specification,
                    ModConfigurations.server.specification,
                )
            }
        }

        @JvmStatic
        @SubscribeEvent
        fun onLoadComplete(event: FMLLoadCompleteEvent) {
            instance.context.registerExtensionPoint(ConfigScreenFactory::class.java) {
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
        }
    }

    private fun initialize() {
        val forgeEventBus = MinecraftForge.EVENT_BUS
        val modEventBus = context.modEventBus
        forgeEventBus.register(this)
        registrate.registerEventListeners(modEventBus)

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT) {
            Runnable {
                PonderIndex.addPlugin(ModPonderPlugin())
            }
        }

        RegistryManager.registerAll()

        ForgeModPackets.register()

        Runtime.getRuntime().addShutdownHook(
            Thread {
                try {
                    AudioPlayerRegistry.clear()
                    ProcessLifecycleManager.shutdownAll()
                } catch (e: Exception) {
                    "Error shutting down processes: ${e.message}".err()
                }
            },
        )
    }

    fun getRegistrate(): CreateRegistrate = registrate
}

val CreateHarmonics: ForgeCreateHarmonicsMod
    get() = ForgeCreateHarmonicsMod.instance

internal val ModEventBus: IEventBus? = ForgeCreateHarmonicsMod.instance.context.modEventBus
internal val ModLoadingContext = ForgeCreateHarmonicsMod.instance.context

internal fun cRegistrate() = CreateHarmonics.getRegistrate()
