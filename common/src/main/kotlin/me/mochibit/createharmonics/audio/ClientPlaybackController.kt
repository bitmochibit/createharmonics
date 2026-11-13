package me.mochibit.createharmonics.audio

import me.mochibit.createharmonics.audio.player.AudioPlayer
import me.mochibit.createharmonics.audio.player.PlayerState
import me.mochibit.createharmonics.content.kinetics.recordPlayer.PlaybackState
import me.mochibit.createharmonics.content.records.RecordPlayback.playFromRecord
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * Client playback controller
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new ClientPlaybackController
 */
object ClientPlaybackController {
    /**
     * Handles
     *
     * @param player
     * @param target
     * @param level
     * @param record
     * @receiver
     * @param positionSeconds the position seconds
     */
    fun apply(player: AudioPlayer, target: PlaybackState, level: Level, record: () -> ItemStack, positionSeconds: Double) {
        when (target) {
            PlaybackState.PLAYING -> when (player.state.value) {
                PlayerState.PAUSED -> player.play()
                PlayerState.STOPPED -> player.playFromRecord(record(), positionSeconds, level)
                else -> Unit
            }
            PlaybackState.PAUSED -> if (player.state.value != PlayerState.PAUSED) player.pause()
            PlaybackState.STOPPED -> if (player.state.value != PlayerState.STOPPED) player.stop()
        }
    }
}