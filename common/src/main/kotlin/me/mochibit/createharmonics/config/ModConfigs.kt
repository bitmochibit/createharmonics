package me.mochibit.createharmonics.config

import net.createmod.catnip.config.ConfigBase
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.common.ModConfigSpec
import java.util.EnumMap

/**
 * In this case it's okay to use forge code for config
 *
 * There are some adapters for fabric that ports forge's config system.
 */
object ModConfigs {
    val configs: EnumMap<ModConfig.Type, ConfigBase> = EnumMap(ModConfig.Type::class.java)

    val common: CommonConfig = registerConfig({ CommonConfig }, ModConfig.Type.COMMON) as CommonConfig
    val client: ClientConfig = registerConfig({ ClientConfig }, ModConfig.Type.CLIENT) as ClientConfig
    val server: ServerConfig = registerConfig({ ServerConfig }, ModConfig.Type.SERVER) as ServerConfig

    fun registerConfig(
        factory: () -> ConfigBase,
        type: ModConfig.Type,
    ): ConfigBase {
        val specPair =
            ModConfigSpec.Builder().configure { builder ->
                val config = factory()
                config.registerAll(builder)
                return@configure config
            }

        val config = specPair.left
        config.specification = specPair.right
        configs[type] = config
        return config
    }
}
