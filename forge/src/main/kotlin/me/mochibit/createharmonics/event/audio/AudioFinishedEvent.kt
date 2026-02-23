package me.mochibit.createharmonics.event.audio

import net.minecraftforge.eventbus.api.Event
import java.util.*

/**
 * Event fired when audio playback ends for a specific player.
 * Used for local audio end events.
 */
class AudioFinishedEvent : Event()

/**
 * Event fired when synchronized audio playback ends across multiple players.
 * This event is fired on clients when they receive notification from the server
 * that audio has ended for a synchronized group.
 *
 * @param playerUUID The UUID of the record player that ended playback
 */
class AudioSyncEndedEvent(val playerUUID: UUID) : Event()
