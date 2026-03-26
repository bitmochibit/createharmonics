package me.mochibit.createharmonics.foundation.registry

import com.simibubi.create.api.stress.BlockStressValues
import me.mochibit.createharmonics.CreateHarmonicsMod.MOD_ID
import me.mochibit.createharmonics.ModLoadingContext
import me.mochibit.createharmonics.config.ModConfigs.configs
import me.mochibit.createharmonics.config.ModConfigs.server
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.config.ModConfigEvent

@Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
object ForgeConfigRegistrar : ForgeRegistry {
    override val registrationOrder: Int = 1

    override fun register() {
        configs.forEach { (type, config) ->
            ModLoadingContext.registerConfig(type, config.specification)
        }

        BlockStressValues.IMPACTS.registerProvider { block ->
            server.modStress.getImpact(block)
        }

        BlockStressValues.CAPACITIES.registerProvider { block ->
            server.modStress.getCapacity(block)
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onLoad(event: ModConfigEvent.Loading) {
        for (config in configs.values) {
            if (config.specification == event.config.getSpec()) {
                config.onLoad()
            }
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onReload(event: ModConfigEvent.Reloading) {
        for (config in configs.values) {
            if (config.specification == event.config.getSpec()) {
                config.onReload()
            }
        }
    }
}
