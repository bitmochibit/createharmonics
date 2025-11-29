package me.mochibit.createharmonics.audio.server

import me.mochibit.createharmonics.network.ModNetworkHandler
import me.mochibit.createharmonics.network.packet.LobbyJoinedPacket
import net.minecraft.server.MinecraftServer
import net.minecraftforge.network.PacketDistributor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong


/**
 * Registry for managing audio player lobbies for synchronization.
 * Handles lobby creation, user tracking, and playback synchronization.
 */
typealias AudioPlayerId = String

object AudioPlayerLobbyRegistry {
    data class AudioPlayerLobby(
        val playerId: String,
        val trackedUsers: MutableSet<String> = mutableSetOf(),
        private val firstPlayTime: AtomicLong = AtomicLong(-1),
    ) {
        val started: Boolean get() = firstPlayTime.get() > 0

        val playTime: Long get() = if (started) System.currentTimeMillis() - firstPlayTime.get() else 0

        fun start() {
            if (firstPlayTime.compareAndSet(-1, System.currentTimeMillis())) {
                // Successfully started
            }
        }
    }

    private val lobbies = ConcurrentHashMap<AudioPlayerId, AudioPlayerLobby>()
    private val userToLobby = ConcurrentHashMap<String, AudioPlayerId>()

    fun getLobby(audioPlayerId: AudioPlayerId): AudioPlayerLobby {
        return lobbies.getOrPut(audioPlayerId) {
            AudioPlayerLobby(
                audioPlayerId
            )
        }
    }

    fun insertUserToLobby(userId: String, audioPlayerId: String): AudioPlayerLobby {
        val lobby = getLobby(audioPlayerId)
        lobby.trackedUsers.add(userId)
        userToLobby[userId] = audioPlayerId
        return lobby
    }

    fun removeUserFromLobby(userId: String) {
        val audioPlayerId = userToLobby.remove(userId) ?: return
        val lobby = lobbies[audioPlayerId] ?: return
        lobby.trackedUsers.remove(userId)

        // Clean up empty lobbies
        if (lobby.trackedUsers.isEmpty()) {
            lobbies.remove(audioPlayerId)
        }
    }

    fun isLobbyStarted(audioPlayerId: AudioPlayerId): Boolean {
        val lobby = lobbies[audioPlayerId] ?: return false
        return lobby.started
    }

    /**
     * Notifies all users in a lobby that playback has started.
     * This is called when the first user starts playback.
     */
    fun notifyLobbyStarted(audioPlayerId: AudioPlayerId, server: MinecraftServer) {
        val lobby = lobbies[audioPlayerId] ?: return
        if (!lobby.started) return

        val packet = LobbyJoinedPacket(
            lobbyStarted = true,
            audioPlayerId = audioPlayerId,
            playTime = lobby.playTime
        )

        // Send to all users in the lobby
        lobby.trackedUsers.forEach { userId ->
            try {
                val uuid = java.util.UUID.fromString(userId)
                val player = server.playerList.getPlayer(uuid)
                if (player != null) {
                    ModNetworkHandler.channel.send(
                        PacketDistributor.PLAYER.with { player },
                        packet
                    )
                }
            } catch (e: IllegalArgumentException) {
                // Invalid UUID format, skip
            }
        }
    }

    fun clear() {
        lobbies.clear()
        userToLobby.clear()
    }
}