package me.mochibit.createharmonics.foundation.services

import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLLoader
import net.neoforged.fml.util.thread.EffectiveSide

class NeoforgePlatformService : PlatformService {
    override val currentPlatform: PlatformService.Platform = PlatformService.Platform.NEOFORGE
    override val environment: PlatformService.Environment
        get() =
            when {
                FMLLoader.getDist().isClient -> PlatformService.Environment.CLIENT
                FMLLoader.getDist().isDedicatedServer -> PlatformService.Environment.SERVER
                else -> throw IllegalStateException("Unknown environment")
            }
    override val currentThreadSide: PlatformService.Environment
        get() = if (EffectiveSide.get().isServer) PlatformService.Environment.SERVER else PlatformService.Environment.CLIENT

    override fun isModLoaded(modId: String): Boolean = ModList.get().isLoaded(modId)
}
