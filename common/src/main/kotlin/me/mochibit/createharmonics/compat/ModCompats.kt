package me.mochibit.createharmonics.compat

import me.mochibit.createharmonics.compat.sable.SableCompat
import me.mochibit.createharmonics.compat.sable.SableCompatImpl
import me.mochibit.createharmonics.foundation.services.platformService

/**
 * Mod compats
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new ModCompats
 */
internal object ModCompats {
    val sableCompat: SableCompat? by lazy {
        if (platformService.isModLoaded("sable")) {
            SableCompatImpl
        } else {
            null
        }
    }
}
