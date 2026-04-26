package me.mochibit.createharmonics

import me.mochibit.createharmonics.CreateHarmonicsMod.MOD_ID
import me.mochibit.createharmonics.config.ModConfigs
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlockEntity
import me.mochibit.createharmonics.content.processing.recordPressBase.RecordPressBaseBlockEntity
import me.mochibit.createharmonics.data.DataGenerators.provideLang
import me.mochibit.createharmonics.foundation.registry.ModBlockEntities
import me.mochibit.createharmonics.foundation.registry.NeoforgeModPackets
import me.mochibit.createharmonics.foundation.registry.NeoforgeRegistry
import me.mochibit.createharmonics.foundation.registry.autoRegister
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent

@Mod(MOD_ID)
class NeoforgeModEntryPoint(
    val modEventBus: IEventBus,
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

    @EventBusSubscriber(modid = MOD_ID)
    object ModSetup {
        @JvmStatic
        @SubscribeEvent
        fun registerCapabilities(event: RegisterCapabilitiesEvent) {
            event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.ANDESITE_JUKEBOX.get(),
            ) { be: RecordPlayerBlockEntity, _ -> be.itemHandler }

            event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.RECORD_PRESS_BASE.get(),
            ) { be: RecordPressBaseBlockEntity, _ -> be.behaviour.itemHandler }
        }
    }

    private fun initialize() {
        CreateHarmonicsMod.commonSetup {
            registerEventListeners(this@NeoforgeModEntryPoint.modEventBus)
        }

        provideLang()

        autoRegister<NeoforgeRegistry>()

        ModEventBus.addListener(NeoforgeModPackets::registerPayloads)
    }
}

internal val ModEventBus: IEventBus = NeoforgeModEntryPoint.instance.modEventBus
