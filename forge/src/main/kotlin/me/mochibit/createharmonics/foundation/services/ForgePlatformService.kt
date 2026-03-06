package me.mochibit.createharmonics.foundation.services

import me.mochibit.createharmonics.foundation.eventbus.ForgeEventBridge
import net.minecraftforge.fml.ModList
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

    override fun isModLoaded(modId: String): Boolean = ModList.get().isLoaded(modId)

    override fun setupEventBridge() {
        ForgeEventBridge.setup()
    }
}
