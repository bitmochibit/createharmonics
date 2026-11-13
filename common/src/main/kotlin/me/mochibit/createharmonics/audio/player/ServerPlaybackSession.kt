package me.mochibit.createharmonics.audio.player

import me.mochibit.createharmonics.foundation.eventbus.AutoHandler
import me.mochibit.createharmonics.foundation.eventbus.ModEventHandler
import me.mochibit.createharmonics.foundation.network.packet.AudioPlayerContextStopPacket
import me.mochibit.createharmonics.foundation.registry.ModPackets
import me.mochibit.createharmonics.foundation.services.eventService
import java.util.UUID


/**
 * Server side playback tracker.
 *
 * @constructor
 * @property owner
 * @param initialClock the initial clock
 */
class ServerPlaybackSession(initialClock: PlaytimeClock, @Volatile var owner: PlaybackSessionOwner?) {
    var clock = initialClock
        private set

    init { clock.pause() }

    /**
     * Companion
     * Companion object — holds factory methods and constants for the enclosing class.
     *
     * @constructor Creates a new Companion
     */
    companion object {
        private const val END_TIMEOUT_TICKS = 200
        private const val STALE_TIMEOUT_TICKS = 200
    }

    private var staleTicks = 0

    private val listeners = HashSet<UUID>()
    private val ended = HashSet<UUID>()
    private var ticksSinceFirstEnd = 0
    private var suspended = false

    /**
     * Rebind
     *
     * @param newOwner the new owner
     * @param newClock the new clock
     */
    fun rebind(newOwner: PlaybackSessionOwner, newClock: PlaytimeClock) {
        if (newClock !== clock) {
            newClock.updateValues(clock.currentPlaytime, clock.isPlaying)
            clock = newClock
        }
        owner = newOwner
        staleTicks = 0
    }

    /**
     * Tick
     *
     * @return `true` if the condition holds, `false` otherwise
     */
    fun tick(): Boolean {
        clock.tick()
        staleTicks = if (owner?.isAlive == false) staleTicks + 1 else 0
        if (staleTicks >= STALE_TIMEOUT_TICKS) return true
        if (ended.isEmpty()) { ticksSinceFirstEnd = 0; return false }
        if (++ticksSinceFirstEnd >= END_TIMEOUT_TICKS) { listeners.retainAll(ended); checkAllEnded() }
        return false
    }

    /**
     * On started
     *
     * @param id
     */
    fun onStarted(id: UUID) {
        ended.remove(id)
        if (listeners.isEmpty()) {
            if (!suspended) clock.play()
            owner?.onClockChanged()
        }
        listeners.add(id)
    }

    /**
     * On failed
     *
     */
    fun onFailed() { owner?.onRestartRequested() }

    /**
     * On ended
     *
     * @param id
     */
    fun onEnded(id: UUID) {
        listeners.add(id)
        ended.add(id)
        checkAllEnded()
    }

    /**
     * On left
     *
     * @param id
     */
    fun onLeft(id: UUID) {
        if (!listeners.remove(id)) return
        ended.remove(id)
        if (listeners.isEmpty()) {
            clock.pause()
            owner?.onClockChanged()
        } else {
            checkAllEnded()
        }
    }


    /**
     * Pause
     *
     */
    fun pause() {
        if (suspended) return
        suspended = true
        clock.pause()
        owner?.onClockChanged()
    }

    /**
     * Resume
     *
     */
    fun resume() {
        if (!suspended) return
        suspended = false
        if (listeners.isNotEmpty()) { clock.play(); owner?.onClockChanged() }
    }

    /**
     * Resets
     *
     */
    fun reset() {
        listeners.clear(); ended.clear()
        ticksSinceFirstEnd = 0; suspended = false
        clock.stop()
    }

    private fun checkAllEnded() {
        if (listeners.isEmpty() || !ended.containsAll(listeners)) return
        listeners.clear(); ended.clear(); ticksSinceFirstEnd = 0
        clock.stop()
        owner?.onAllEnded()
    }
}

/**
 * Playback session owner
 *
 * @property isAlive the is alive
 */
interface PlaybackSessionOwner {
    /**
     * On clock changed
     *
     */
    fun onClockChanged()

    /**
     * On all ended
     *
     */
    fun onAllEnded()

    /**
     * On restart requested
     *
     */
    fun onRestartRequested()

    /**
     * On title changed
     *
     * @param title
     */
    fun onTitleChanged(title: String) {}
    val isAlive: Boolean get() = true
}

/**
 * Playback sessions
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new PlaybackSessions
 */
@AutoHandler
object PlaybackSessions : ModEventHandler {
    private val sessions = HashMap<String, ServerPlaybackSession>()

    /**
     * Returns
     *
     * @param id
     * @return the server playback session
     */
    fun get(id: String): ServerPlaybackSession? = sessions[id]


    /**
     * Acquire
     *
     * @param id
     * @param owner
     * @param clock
     * @return the server playback session
     */
    fun acquire(id: String, owner: PlaybackSessionOwner, clock: PlaytimeClock): ServerPlaybackSession =
        sessions.compute(id) { _, old -> old?.also { it.rebind(owner, clock) } ?: ServerPlaybackSession(clock, owner) }!!

    /**
     * Release
     *
     * @param id
     */
    fun release(id: String) {
        if (sessions.remove(id) == null) return
        ModPackets.broadcast(AudioPlayerContextStopPacket(id))
    }

    /**
     * Removes the listener
     *
     * @param playerUuid the player uuid
     */
    fun removeListener(playerUuid: UUID) = sessions.values.toList().forEach { it.onLeft(playerUuid) }

    /**
     * Tick
     *
     */
    fun tick() {
        val it = sessions.entries.iterator()
        while (it.hasNext()) {
            val (id, s) = it.next()
            if (s.tick()) { it.remove(); ModPackets.broadcast(AudioPlayerContextStopPacket(id)) }
        }
    }

    /**
     * Removes
     *
     */
    fun clear() = sessions.clear()

    override fun setupEvents() {
        eventService.onServerTick { tick() }
        eventService.onPlayerLogout { player ->
            removeListener(player.uuid)
        }
        eventService.onServerStopped {
            clear()
        }

    }
}