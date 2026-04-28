package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.foundation.eventbus.PlatformEventBridge
import me.mochibit.createharmonics.foundation.info
import me.mochibit.createharmonics.foundation.services.PlatformService
import me.mochibit.createharmonics.foundation.services.platformService

object ModEventProxy : CommonRegistry {
    override fun register() {
        "Registering mod event proxies...".info()
        val isClient = platformService isEnvironment PlatformService.Environment.CLIENT
        platformService.setupEventBridge()
        if (isClient) {
            platformService.setupClientEventBridge()
        }
        PlatformEventBridge.validateAll(isClient)
    }
}
