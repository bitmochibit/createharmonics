package me.mochibit.createharmonics.foundation.services

import me.mochibit.createharmonics.foundation.eventbus.NeoforgeClientEventBridge
import me.mochibit.createharmonics.foundation.eventbus.NeoforgeCommonEventBridge
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLLoader

class NeoforgePlatformService : PlatformService {
    override val currentPlatform: PlatformService.Platform = PlatformService.Platform.NEOFORGE
    override val environment: PlatformService.Environment
        get() =
            when {
                FMLLoader.getDist().isClient -> PlatformService.Environment.CLIENT
                FMLLoader.getDist().isDedicatedServer -> PlatformService.Environment.SERVER
                else -> throw IllegalStateException("Unknown environment")
            }

    override fun isModLoaded(modId: String): Boolean = ModList.get().isLoaded(modId)

    override fun setupEventBridge() {
        NeoforgeCommonEventBridge.setup()
    }

    override fun setupClientEventBridge() {
        NeoforgeClientEventBridge.setup()
    }
}
