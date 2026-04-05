package me.mochibit.createharmonics.foundation.registry

import com.simibubi.create.api.behaviour.display.DisplaySource
import com.tterrag.registrate.util.entry.RegistryEntry
import me.mochibit.createharmonics.ModRegistrate
import me.mochibit.createharmonics.content.kinetics.recordPlayer.displaySource.AudioNameDisplaySource
import me.mochibit.createharmonics.content.kinetics.recordPlayer.displaySource.PlayerStatusDisplaySource
import me.mochibit.createharmonics.foundation.info

object ModDisplaySources : CommonRegistry {
    override val registrationOrder = 1

    val AUDIO_NAME: RegistryEntry<DisplaySource, AudioNameDisplaySource> =
        ModRegistrate
            .displaySource("audio_name", ::AudioNameDisplaySource)
            .register()

    val PLAYER_STATUS: RegistryEntry<DisplaySource, PlayerStatusDisplaySource> =
        ModRegistrate
            .displaySource("record_player_status", ::PlayerStatusDisplaySource)
            .register()

    override fun register() {
        "Registering display sources".info()
    }
}
