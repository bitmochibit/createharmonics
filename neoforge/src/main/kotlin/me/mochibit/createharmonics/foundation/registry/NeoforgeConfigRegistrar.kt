package me.mochibit.createharmonics.foundation.registry

import com.simibubi.create.api.stress.BlockStressValues
import me.mochibit.createharmonics.CreateHarmonicsMod.MOD_ID
import me.mochibit.createharmonics.config.ModConfigs.configs
import me.mochibit.createharmonics.config.ModConfigs.server
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.config.ModConfigEvent

@EventBusSubscriber(modid = MOD_ID)
object NeoforgeConfigRegistrar : NeoforgeRegistry {
    override val registrationOrder: Int = 1

    override fun register() {
        configs.forEach { (type, config) ->
            ModLoadingContext.get().activeContainer.registerConfig(type, config.specification)
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
            if (config.specification == event.config.spec) {
                config.onLoad()
            }
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onReload(event: ModConfigEvent.Reloading) {
        for (config in configs.values) {
            if (config.specification == event.config.spec) {
                config.onReload()
            }
        }
    }
}
