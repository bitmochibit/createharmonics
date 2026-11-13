package me.mochibit.createharmonics.audio

import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.instance.StreamingSoundInstance
import me.mochibit.createharmonics.audio.player.AudioPlayer
import me.mochibit.createharmonics.audio.player.SoundInstanceFactory
import me.mochibit.createharmonics.foundation.err
import me.mochibit.createharmonics.foundation.eventbus.AutoHandler
import me.mochibit.createharmonics.foundation.eventbus.ModEventHandler
import me.mochibit.createharmonics.foundation.services.eventService
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.level.Level
import java.util.concurrent.ConcurrentHashMap

val DefaultSoundInstanceFactory: SoundInstanceFactory = { stream ->
    StreamingSoundInstance.simpleFactory(this, stream, SoundEvents.EMPTY)
}

/**
 * Audio player manager
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new AudioPlayerManager
 */
@AutoHandler
object AudioPlayerManager: ModEventHandler {
    private val players = ConcurrentHashMap<String, AudioPlayer>()


    /**
     * Returns the or create
     *
     * @param id
     * @param provider
     * @param effectChainConfiguration the effect chain configuration
     * @receiver
     * @return the audio player
     */
    fun getOrCreate(
        id: String,
        provider: SoundInstanceFactory = DefaultSoundInstanceFactory,
        effectChainConfiguration: EffectChain.(AudioPlayer) -> Unit = {},
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

    /**
     * Returns
     *
     * @param id
     * @return the audio player
     */
    fun get(id: String): AudioPlayer? = players[id]

    /**
     * Release
     *
     * @param id
     */
    fun release(
        id: String
    ) {
        players.remove(id)?.close()
    }

    /**
     * Stops the all
     *
     */
    fun closeAll() {
        val snapshot = players.values.toList().also { players.clear() }
        snapshot.forEach { player ->
            runCatching { player.close() }
                .onFailure { "Error disposing ${player.playerId}: ${it.message}".err() }
        }
    }


    /**
     * Exists
     *
     * @param id
     * @return `true` if the condition holds, `false` otherwise
     */
    fun exists(id: String): Boolean = players.containsKey(id)

    private fun notifyBlockUpdate(level: Level, pos: BlockPos) {
        if (players.isEmpty()) return
        for (player in players.values) {
            player.spatial.notifyBlockUpdate(player, level, pos)
        }
    }

    override fun setupEvents() {
        eventService.onBlockNeighborNotify { level, pos, state ->
            if (!level.isClientSide) return@onBlockNeighborNotify
            notifyBlockUpdate(level, pos)
        }
    }
}
