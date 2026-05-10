package me.mochibit.createharmonics.compat

import me.mochibit.createharmonics.compat.vs.VsCompat
import me.mochibit.createharmonics.compat.vs.VsCompatImpl
import me.mochibit.createharmonics.foundation.services.platformService

internal object ModCompats {
    val vsCompat: VsCompat? by lazy {
        if (platformService.isModLoaded("valkyrienskies")) {
            VsCompatImpl
        } else {
            null
        }
    }
}
