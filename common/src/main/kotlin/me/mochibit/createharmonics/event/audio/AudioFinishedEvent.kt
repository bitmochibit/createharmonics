package me.mochibit.createharmonics.event.audio

import me.mochibit.createharmonics.foundation.eventbus.ModEvent
import java.util.UUID

data class AudioFinishedEvent(
    val playerUUID: UUID,
) : ModEvent

data class AudioSyncEndedEvent(
    val playerUUID: UUID,
) : ModEvent
