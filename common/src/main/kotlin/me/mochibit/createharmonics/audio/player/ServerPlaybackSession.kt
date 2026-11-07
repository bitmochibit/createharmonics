package me.mochibit.createharmonics.audio.player

import me.mochibit.createharmonics.foundation.eventbus.AutoHandler
import me.mochibit.createharmonics.foundation.eventbus.ModEventHandler
import me.mochibit.createharmonics.foundation.network.packet.AudioPlayerContextStopPacket
import me.mochibit.createharmonics.foundation.registry.ModPackets
import me.mochibit.createharmonics.foundation.services.eventService
import java.util.UUID

//TODO refactor the audio package to clarify what code is client only

/**
 * Server side playback tracker.
 *
 */
class ServerPlaybackSession(val clock: PlaytimeClock) {
    init { clock.pause() }

    companion object {
        private const val END_TIMEOUT_TICKS = 200
    }

    var owner: PlaybackSessionOwner? = null


    private val listeners = HashSet<UUID>()
    private val ended = HashSet<UUID>()
    private var ticksSinceFirstEnd = 0
    private var suspended = false

    fun tick() {
        clock.tick()
        if (ended.isEmpty()) { ticksSinceFirstEnd = 0; return }
        if (++ticksSinceFirstEnd >= END_TIMEOUT_TICKS) {
            listeners.retainAll(ended)
            checkAllEnded()
        }
    }

    fun onStarted(id: UUID) {
        ended.remove(id)
        if (listeners.isEmpty()) {
            if (!suspended) clock.play()
            owner?.onClockChanged()
        }
        listeners.add(id)
    }

    fun onFailed() { owner?.onRestartRequested() }

    fun onEnded(id: UUID) {
        listeners.add(id)
        ended.add(id)
        checkAllEnded()
    }

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

    fun restore(offset: Double) {
        if (listeners.isEmpty() && !clock.isPlaying) clock.updateValues(offset, false)
    }

    fun pause() {
        if (suspended) return
        suspended = true
        clock.pause()
        owner?.onClockChanged()
    }

    fun resume() {
        if (!suspended) return
        suspended = false
        if (listeners.isNotEmpty()) { clock.play(); owner?.onClockChanged() }
    }

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

interface PlaybackSessionOwner {
    fun onClockChanged()
    fun onAllEnded()
    fun onRestartRequested()
    fun onTitleChanged(title: String) {}
}

@AutoHandler
object PlaybackSessions : ModEventHandler {
    private val sessions = HashMap<String, ServerPlaybackSession>()

    fun get(id: String): ServerPlaybackSession? = sessions[id]


    fun acquire(id: String, owner: PlaybackSessionOwner, clock: PlaytimeClock) =
        ServerPlaybackSession(clock).also { it.owner = owner; sessions[id] = it }

    fun release(id: String) {
        if (sessions.remove(id) == null) return
        ModPackets.broadcast(AudioPlayerContextStopPacket(id))
    }

    fun removeListener(playerUuid: UUID) = sessions.values.toList().forEach { it.onLeft(playerUuid) }

    fun tick() = sessions.values.forEach { it.tick() }

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