package me.mochibit.createharmonics.audio.player

import kotlinx.serialization.Serializable
import me.mochibit.createharmonics.foundation.network.packet.AudioPlayerListenerPacket
import me.mochibit.createharmonics.foundation.network.packet.UpdateAudioNamePacket
import me.mochibit.createharmonics.foundation.registry.ModPackets

/**
 * Listener event
 * Enum — a fixed set of named constants.
 *
 * @constructor Creates a new ListenerEvent
 */
@Serializable
enum class ListenerEvent {
    /**
     * Started
     *
     * @constructor Creates a new STARTED
     */
    STARTED,

    /**
     * Ended
     *
     * @constructor Creates a new ENDED
     */
    ENDED,

    /**
     * Left
     *
     * @constructor Creates a new LEFT
     */
    LEFT,

    /**
     * Failed
     *
     * @constructor Creates a new FAILED
     */
    FAILED }

/**
 * Audio player notifier
 *
 * @constructor Creates a new AudioPlayerNotifier
 * @property playerId the player id
 */
class AudioPlayerNotifier(private val playerId: String) {
    /**
     * Audio title changed
     *
     * @param title
     */
    fun audioTitleChanged(title: String) = ModPackets.sendToServer(UpdateAudioNamePacket(playerId, title))

    /**
     * Listener started
     *
     */
    fun listenerStarted() = send(ListenerEvent.STARTED)

    /**
     * Stream ended
     *
     */
    fun streamEnded() = send(ListenerEvent.ENDED)

    /**
     * Listener left
     *
     */
    fun listenerLeft() = send(ListenerEvent.LEFT)

    /**
     * Listener failed
     *
     */
    fun listenerFailed() = send(ListenerEvent.FAILED)

    private fun send(event: ListenerEvent) =
        ModPackets.sendToServer(AudioPlayerListenerPacket(playerId, event))
}