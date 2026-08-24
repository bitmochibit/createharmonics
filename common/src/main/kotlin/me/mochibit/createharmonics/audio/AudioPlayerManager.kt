package me.mochibit.createharmonics.audio

import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.player.AudioPlayer
import me.mochibit.createharmonics.audio.player.SoundInstanceFactory
import me.mochibit.createharmonics.foundation.err
import me.mochibit.createharmonics.foundation.services.eventService
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import java.util.concurrent.ConcurrentHashMap

object AudioPlayerManager {
    private val players = ConcurrentHashMap<String, AudioPlayer>()

    init {
        eventService.onBlockNeighborNotify { level, pos, state ->
            if (!level.isClientSide) return@onBlockNeighborNotify
            notifyBlockUpdate(level, pos)
        }
    }

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

    private fun notifyBlockUpdate(level: Level, pos: BlockPos) {
        if (players.isEmpty()) return
        for (player in players.values) {
            player.spatial.notifyBlockUpdate(player, level, pos)
        }
    }
}
