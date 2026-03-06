package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.foundation.info
import me.mochibit.createharmonics.foundation.services.platformService

object ModEventProxy : CommonRegistry {
    override fun register() {
        "Registering mod event proxies...".info()
        platformService.setupEventBridge()
    }
}
