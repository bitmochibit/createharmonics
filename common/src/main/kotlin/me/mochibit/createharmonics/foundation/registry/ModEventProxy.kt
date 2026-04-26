package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.foundation.info
import me.mochibit.createharmonics.foundation.services.PlatformService
import me.mochibit.createharmonics.foundation.services.platformService

object ModEventProxy : CommonRegistry {
    override fun register() {
        "Registering mod event proxies...".info()
        platformService.setupEventBridge()
        if (platformService isEnvironment PlatformService.Environment.CLIENT) {
            platformService.setupClientEventBridge()
        }
    }
}
