package me.mochibit.createharmonics.event.audio

import dev.architectury.event.Event
import dev.architectury.event.EventFactory
import java.util.UUID

/**
 * Event fired when audio playback ends for a specific player.
 * Used for local audio end events.
 */
object AudioFinishedEvent {
    @JvmField
    val EVENT: Event<Listener> = EventFactory.createLoop()

    fun interface Listener {
        fun onAudioFinished(playerUUID: UUID)
    }
}

/**
 * Event fired when synchronized audio playback ends across multiple players.
 * This event is fired on clients when they receive notification from the server
 * that audio has ended for a synchronized group.
 *
 * @param playerUUID The UUID of the record player that ended playback
 */
object AudioSyncEndedEvent {
    @JvmField
    val EVENT: Event<Listener> = EventFactory.createLoop()

    fun interface Listener {
        fun onAudioSyncEnded(playerUUID: UUID)
    }
}
