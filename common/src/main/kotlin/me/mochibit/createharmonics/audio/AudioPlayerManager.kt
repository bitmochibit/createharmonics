package me.mochibit.createharmonics.audio

import kotlinx.coroutines.launch
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.player.AudioPlayer
import me.mochibit.createharmonics.audio.player.SoundInstanceFactory
import me.mochibit.createharmonics.foundation.async.ModCoroutineScope
import me.mochibit.createharmonics.foundation.err
import me.mochibit.createharmonics.foundation.eventbus.CommonEvents
import me.mochibit.createharmonics.foundation.eventbus.EventBus
import me.mochibit.createharmonics.foundation.eventbus.ProxyEvent
import me.mochibit.createharmonics.foundation.info
import java.util.concurrent.ConcurrentHashMap

object AudioPlayerManager {
    private val players = ConcurrentHashMap<String, AudioPlayer>()

    fun getOrCreate(
        id: String,
        provider: SoundInstanceFactory,
        effectChainConfiguration: EffectChain.(player: AudioPlayer) -> Unit,
    ): AudioPlayer {
        require(id.isNotBlank()) { "Player ID cannot be blank" }

        val player =
            players.computeIfAbsent(id) { key ->
                AudioPlayer(
                    playerId = key,
                    soundInstanceFactory = provider,
                ).also { newPlayer ->
                    newPlayer.effectChain.effectChainConfiguration(newPlayer)
                }
            }

        return player
    }

    fun get(id: String): AudioPlayer? = players[id]

    fun release(
        id: String
    ) {
        players.remove(id)?.close()
    }

    fun closeAll() {
        val snapshot = players.values.toList().also { players.clear() }
        snapshot.forEach { player ->
            runCatching { player.close() }
                .onFailure { "Error disposing ${player.playerId}: ${it.message}".err() }
        }
    }


    fun exists(id: String): Boolean = players.containsKey(id)
}
