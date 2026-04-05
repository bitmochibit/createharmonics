package me.mochibit.createharmonics

import me.mochibit.createharmonics.CreateHarmonicsMod.MOD_ID
import me.mochibit.createharmonics.config.ModConfigs
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlockEntity
import me.mochibit.createharmonics.content.processing.recordPressBase.RecordPressBaseBlockEntity
import me.mochibit.createharmonics.foundation.registry.ModBlockEntities
import me.mochibit.createharmonics.foundation.registry.NeoForgeRegistry
import me.mochibit.createharmonics.foundation.registry.autoRegister
import me.mochibit.createharmonics.ponder.ModPonderPlugin
import net.createmod.catnip.config.ui.BaseConfigScreen
import net.createmod.ponder.foundation.PonderIndex
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.fml.common.Mod.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.client.ConfigScreenHandler.ConfigScreenFactory
import net.neoforged.neoforge.common.NeoForge

@Mod(MOD_ID)
class NeoforgeModEntryPoint(
    // NeoForge inietta l'IEventBus del mod direttamente nel costruttore
    private val modEventBus: IEventBus,
) {
    companion object {
        @JvmStatic
        lateinit var instance: NeoforgeModEntryPoint
            private set
    }

    init {
        instance = this
        initialize()
    }

    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    object ModSetup {
        @JvmStatic
        @SubscribeEvent
        fun onClientSetup(event: FMLClientSetupEvent) {
            BaseConfigScreen.setDefaultActionFor(MOD_ID) { base ->
                base.withSpecs(
                    ModConfigs.client.specification,
                    ModConfigs.common.specification,
                    ModConfigs.server.specification,
                )
            }
        }

        @JvmStatic
        @SubscribeEvent
        fun onLoadComplete(event: FMLLoadCompleteEvent) {
            // ModLoadingContext qui è net.neoforged.fml.ModLoadingContext
            ModLoadingContext.get().registerExtensionPoint(ConfigScreenFactory::class.java) {
                ConfigScreenFactory { _: Minecraft, previousScreen: Screen ->
                    BaseConfigScreen(previousScreen, MOD_ID)
                }
            }
        }

        @JvmStatic
        @SubscribeEvent
        fun registerCapabilities(event: RegisterCapabilitiesEvent) {
            event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.RECORD_PLAYER.get(),
            ) { be: RecordPlayerBlockEntity, _ -> be.itemHandler }

            event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.RECORD_PRESS_BASE.get(),
            ) { be: RecordPressBaseBlockEntity, _ -> be.behaviour.itemHandler }
        }
    }

    private fun initialize() {
        NeoForge.EVENT_BUS.register(this)

        // DistExecutor è rimosso in NeoForge; si usa FMLEnvironment.dist direttamente
        if (FMLEnvironment.dist.isClient) {
            PonderIndex.addPlugin(ModPonderPlugin())
        }

        CreateHarmonicsMod.commonSetup {
            registerEventListeners(modEventBus)
        }

        autoRegister<NeoForgeRegistry>()
    }
}

internal val ModEventBus: IEventBus = NeoforgeModEntryPoint.instance.modEventBus
internal val NeoModLoadingContext get() = ModLoadingContext.get()
