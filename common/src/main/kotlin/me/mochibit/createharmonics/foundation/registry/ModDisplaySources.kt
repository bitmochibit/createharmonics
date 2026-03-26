package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.ModRegistrate
import me.mochibit.createharmonics.content.kinetics.recordPlayer.AudioNameDisplaySource
import me.mochibit.createharmonics.foundation.info

object ModDisplaySources : CommonRegistry {
    override val registrationOrder = 1

    val AUDIO_NAME =
        ModRegistrate
            .displaySource("audio_name", ::AudioNameDisplaySource)
            .register()

    override fun register() {
        "Registering display sources".info()
    }
}
