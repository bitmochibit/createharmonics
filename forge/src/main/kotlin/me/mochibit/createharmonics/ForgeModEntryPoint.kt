package me.mochibit.createharmonics

import me.mochibit.createharmonics.CreateHarmonicsMod.MOD_ID
import me.mochibit.createharmonics.config.ModConfigs
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlockEntity
import me.mochibit.createharmonics.content.processing.recordPressBase.RecordPressBaseBlockEntity
import me.mochibit.createharmonics.foundation.extension.asResource
import me.mochibit.createharmonics.foundation.registry.ForgeRegistry
import me.mochibit.createharmonics.foundation.registry.autoRegister
import net.createmod.catnip.config.ui.BaseConfigScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.Direction
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.capabilities.ICapabilityProvider
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.event.AttachCapabilitiesEvent
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.DistExecutor
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext

@Mod(MOD_ID)
class ForgeModEntryPoint(
    val context: FMLJavaModLoadingContext,
) {
    companion object {
        @JvmStatic
        lateinit var instance: ForgeModEntryPoint
            private set
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
                    ModConfigs.client.specification,
                    ModConfigs.common.specification,
                    ModConfigs.server.specification,
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
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    object ForgeEvents {
        @SubscribeEvent
        fun onAttachCapabilities(event: AttachCapabilitiesEvent<BlockEntity>) {
            when (val be = event.`object`) {
                is RecordPlayerBlockEntity -> {
                    event.addCapability(
                        "item_handler".asResource(),
                        object : ICapabilityProvider {
                            override fun <T> getCapability(
                                cap: Capability<T>,
                                side: Direction?,
                            ): LazyOptional<T> = ForgeCapabilities.ITEM_HANDLER.orEmpty(cap, be.lazyItemHandler.cast())
                        },
                    )
                }

                is RecordPressBaseBlockEntity -> {
                    event.addCapability(
                        "item_handler".asResource(),
                        object : ICapabilityProvider {
                            override fun <T> getCapability(
                                cap: Capability<T>,
                                side: Direction?,
                            ): LazyOptional<T> = ForgeCapabilities.ITEM_HANDLER.orEmpty(cap, be.behaviour.lazyItemHandler.cast())
                        },
                    )
                }
            }
        }
    }

    private fun initialize() {
        MinecraftForge.EVENT_BUS.register(this)

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT) {
            Runnable {
                CreateHarmonicsClientMod.setup()
            }
        }

        CreateHarmonicsMod.commonSetup {
            registerEventListeners(context.modEventBus)
        }

        autoRegister<ForgeRegistry>()
    }
}

internal val ModEventBus: IEventBus? = ForgeModEntryPoint.instance.context.modEventBus
internal val ModLoadingContext = ForgeModEntryPoint.instance.context
