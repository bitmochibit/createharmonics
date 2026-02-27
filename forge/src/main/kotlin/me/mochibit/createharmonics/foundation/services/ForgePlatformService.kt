package me.mochibit.createharmonics.foundation.services

import net.minecraftforge.fml.loading.FMLLoader

class ForgePlatformService : PlatformService {
    override val platformName: String = "Forge"
    override val environment: PlatformService.Environment
        get() =
            when {
                FMLLoader.getDist().isClient -> PlatformService.Environment.CLIENT
                FMLLoader.getDist().isDedicatedServer -> PlatformService.Environment.SERVER
                else -> throw IllegalStateException("Unknown environment")
            }
}
