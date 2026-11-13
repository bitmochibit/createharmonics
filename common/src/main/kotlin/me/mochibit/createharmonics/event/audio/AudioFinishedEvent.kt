package me.mochibit.createharmonics.event.audio

import me.mochibit.createharmonics.foundation.eventbus.ModEvent
import java.util.UUID

/**
 * Audio finished event
 * Data class — a value holder; `equals`, `hashCode`, `toString` and `copy` are generated.
 *
 * @constructor Creates a new AudioFinishedEvent
 * @property playerUUID the player uuid
 */
data class AudioFinishedEvent(
    val playerUUID: UUID,
) : ModEvent

/**
 * Audio sync ended event
 * Data class — a value holder; `equals`, `hashCode`, `toString` and `copy` are generated.
 *
 * @constructor Creates a new AudioSyncEndedEvent
 * @property playerUUID the player uuid
 */
data class AudioSyncEndedEvent(
    val playerUUID: UUID,
) : ModEvent
