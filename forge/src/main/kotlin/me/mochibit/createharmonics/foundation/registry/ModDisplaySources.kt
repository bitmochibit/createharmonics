package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.cRegistrate
import me.mochibit.createharmonics.content.kinetics.recordPlayer.AudioNameDisplaySource
import me.mochibit.createharmonics.foundation.info

object ModDisplaySources : ForgeRegistry {
    override val registrationOrder = 1

    val AUDIO_NAME =
        cRegistrate()
            .displaySource("audio_name", ::AudioNameDisplaySource)
            .register()

    override fun register() {
        "Registering display sources".info()
    }
}
