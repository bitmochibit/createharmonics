package me.mochibit.createharmonics.audio

import kotlinx.coroutines.Dispatchers
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.foundation.async.modLaunch
import me.mochibit.createharmonics.foundation.err
import me.mochibit.createharmonics.foundation.eventbus.EventBus
import me.mochibit.createharmonics.foundation.eventbus.ProxyEvent
import java.util.concurrent.ConcurrentHashMap

object AudioPlayerManager {
    private val players = ConcurrentHashMap<String, AudioPlayer>()

    init {
        EventBus.on<ProxyEvent.LevelUnloadEventProxy> { event ->
            closeAll()
        }
    }

    fun create(
        id: String,
        provider: StreamingSoundInstanceProvider,
        sampleRate: Int = 48_000,
        onEffectChainCreate: ((effectChain: EffectChain) -> Unit)? = null,
    ): AudioPlayer {
        require(id.isNotBlank()) { "Player ID cannot be blank" }
        require(!players.containsKey(id)) { "Player '$id' already exists — use get() or release() first" }

        val player =
            AudioPlayer(
                playerId = id,
                soundInstanceProvider = provider,
                sampleRate = sampleRate,
                onEffectChainCreate = onEffectChainCreate,
            )
        players[id] = player
        return player
    }

    fun getOrCreate(
        id: String,
        provider: StreamingSoundInstanceProvider,
        sampleRate: Int = 48_000,
        onEffectChainCreate: ((effectChain: EffectChain) -> Unit)? = null,
    ): AudioPlayer = players[id] ?: create(id, provider, sampleRate, onEffectChainCreate)

    fun get(id: String): AudioPlayer? = players[id]

    fun release(id: String) {
        players.remove(id)?.dispose()
    }

    fun closeAll() {
        val snapshot = players.values.toList().also { players.clear() }

        modLaunch(Dispatchers.IO) {
            snapshot.forEach { player ->
                runCatching { player.dispose() }
                    .onFailure { "Error disposing ${player.playerId}: ${it.message}".err() }
            }
        }
    }

    fun containsStream(id: String): Boolean = players.containsKey(id)
}
