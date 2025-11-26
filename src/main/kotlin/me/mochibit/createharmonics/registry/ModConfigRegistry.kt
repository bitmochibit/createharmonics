package me.mochibit.createharmonics.registry

import me.mochibit.createharmonics.CommonConfig
import me.mochibit.createharmonics.CreateHarmonicsMod
import net.createmod.catnip.config.ConfigBase
import net.minecraftforge.common.ForgeConfigSpec
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.config.ModConfig
import net.minecraftforge.fml.event.config.ModConfigEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import java.util.*

@Mod.EventBusSubscriber(modid = CreateHarmonicsMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
object ModConfigRegistry : AbstractModRegistry {
    val configs: EnumMap<ModConfig.Type, ConfigBase> = EnumMap(ModConfig.Type::class.java)

    val common: CommonConfig = registerConfig({ CommonConfig }, ModConfig.Type.COMMON) as CommonConfig

    fun registerConfig(factory: () -> ConfigBase, type: ModConfig.Type): ConfigBase {
        val specPair = ForgeConfigSpec.Builder().configure { builder ->
            val config = factory()
            config.registerAll(builder)
            return@configure config
        }

        val config = specPair.left
        config.specification = specPair.right
        configs[type] = config
        return config
    }

    override fun register(
        eventBus: IEventBus,
        context: FMLJavaModLoadingContext
    ) {
        configs.forEach { (type, config) ->
            context.registerConfig(type, config.specification)
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onLoad(event: ModConfigEvent.Loading) {
        for (config in configs.values) {
            if (config.specification == event.config.getSpec())
                config.onLoad()
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onReload(event: ModConfigEvent.Reloading) {
        for (config in configs.values) {
            if (config.specification == event.config.getSpec())
                config.onReload()
        }
    }


}