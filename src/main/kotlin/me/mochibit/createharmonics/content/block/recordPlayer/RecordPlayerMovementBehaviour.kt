package me.mochibit.createharmonics.content.block.recordPlayer

import com.simibubi.create.api.behaviour.movement.MovementBehaviour
import com.simibubi.create.content.contraptions.behaviour.MovementContext
import com.simibubi.create.content.contraptions.render.ActorVisual
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.audio.AudioPlayer
import me.mochibit.createharmonics.audio.AudioPlayerRegistry
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.effect.pitchShift.PitchFunction
import me.mochibit.createharmonics.audio.effect.pitchShift.PitchShiftEffect
import me.mochibit.createharmonics.audio.instance.MovingSoundInstance
import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerBlockEntity.Companion.MAX_PITCH
import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerBlockEntity.Companion.MIN_PITCH
import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerBlockEntity.PlaybackState
import me.mochibit.createharmonics.content.item.EtherealRecordItem
import me.mochibit.createharmonics.content.item.EtherealRecordItem.Companion.getAudioUrl
import me.mochibit.createharmonics.extension.onClient
import me.mochibit.createharmonics.extension.onServer
import me.mochibit.createharmonics.extension.remapTo
import me.mochibit.createharmonics.network.ModNetworkHandler
import me.mochibit.createharmonics.network.packet.AudioPlayerContextStopPacket
import net.minecraft.core.BlockPos
import net.minecraftforge.network.PacketDistributor
import java.util.*
import kotlin.math.abs


/**
 * Handles Record Player behavior when attached to a moving contraption.
 * Uses block entity data (synced from server) to determine playback state.
 * Audio streams are managed by AudioPlayer's internal registry using the player block's UUID.
 */
class RecordPlayerMovementBehaviour : MovementBehaviour {

    companion object {
        private const val PLAYBACK_STATE_KEY = "PlaybackState"
        private const val PLAY_TIME_KEY = "PlayTime"
        private const val PLAYER_UUID_KEY = "RecordPlayerUUID"
    }


    override fun stopMoving(context: MovementContext) {
        context.world.onServer {
            ModNetworkHandler.channel.send(
                PacketDistributor.ALL.noArg(),
                AudioPlayerContextStopPacket(getPlayerUUID(context).toString())
            )
        }
    }

    override fun tick(context: MovementContext) {
        // Deterministically calculate playback state on BOTH server and client
        // This ensures they stay synchronized without network packets
        val currentSpeed = abs(context.animationSpeed)
        val hasRecord = hasRecord(context)
        val currentState = getPlaybackState(context)

        val newState = when {
            !hasRecord -> PlaybackState.STOPPED
            currentSpeed == 0f -> {
                // Auto-pause when contraption stops moving
                if (currentState == PlaybackState.PLAYING) PlaybackState.PAUSED
                else currentState
            }

            currentSpeed > 0f -> {
                // Auto-resume when contraption starts moving (unless manually paused)
                when (currentState) {
                    PlaybackState.PAUSED, PlaybackState.STOPPED -> PlaybackState.PLAYING
                    PlaybackState.MANUALLY_PAUSED -> currentState // Respect manual pause
                    else -> currentState
                }
            }

            else -> currentState
        }

        if (newState != currentState) {
            updatePlaybackState(context, newState)
        }

        // Client-side only: manage audio player based on the calculated state
        context.world.onClient {
            handleClientPlayback(context, newState)
        }
    }

    /**
     * Client-side only: Handle audio playback based on calculated state
     * State is calculated deterministically on both sides, so no sync needed
     */
    private fun handleClientPlayback(context: MovementContext, playbackState: PlaybackState) {
        val uuid = getPlayerUUID(context) ?: return
        val playerId = uuid.toString()
        val audioUrl = getAudioUrl(context)

        // Get or create audio player
        val player = AudioPlayerRegistry.getOrCreatePlayer(playerId) {
            Logger.info("Creating audio player for moving contraption: $playerId at ${context.localPos}")
            AudioPlayer(
                { streamId, stream ->
                    MovingSoundInstance(
                        stream,
                        streamId,
                        posSupplier = { BlockPos.containing(context.position) },
                    )
                },
                playerId
            )
        }

        // Handle state changes based on calculated state
        when (playbackState) {
            PlaybackState.PLAYING -> {
                when (player.state) {
                    AudioPlayer.PlayState.STOPPED -> {
                        if (audioUrl != null) {
                            Logger.info("Starting audio for player $playerId")
                            startClientPlayer(player, audioUrl, context)
                        }
                    }

                    AudioPlayer.PlayState.PAUSED -> {
                        Logger.info("Resuming audio for player $playerId")
                        player.resume()
                    }

                    else -> {} // Already playing
                }
            }

            PlaybackState.PAUSED, PlaybackState.MANUALLY_PAUSED -> {
                if (player.state == AudioPlayer.PlayState.PLAYING) {
                    Logger.info("Pausing audio for player $playerId")
                    player.pause()
                }
            }

            PlaybackState.STOPPED -> {
                if (player.state != AudioPlayer.PlayState.STOPPED) {
                    Logger.info("Stopping audio for player $playerId")
                    player.stop()
                }
            }
        }
    }

    /**
     * Update the playback state locally (runs on both server and client)
     * No synchronization needed - state is calculated deterministically on both sides
     */
    private fun updatePlaybackState(context: MovementContext, newState: PlaybackState) {
        val oldState = getPlaybackState(context)

        // Store in context.data (local to this side, not synced)
        context.data.putString(PLAYBACK_STATE_KEY, newState.name)

        when {
            newState == PlaybackState.STOPPED -> {
                context.data.putLong(PLAY_TIME_KEY, 0)
            }

            newState == PlaybackState.PLAYING && oldState != PlaybackState.PLAYING -> {
                context.data.putLong(PLAY_TIME_KEY, System.currentTimeMillis())
            }
        }

        Logger.info("Updated playback state from $oldState to $newState for ${context.localPos}")
    }

    /**
     * Get current playback state from context.data (local state, calculated deterministically)
     */
    private fun getPlaybackState(context: MovementContext): PlaybackState {
        if (!context.data.contains(PLAYBACK_STATE_KEY)) {
            return PlaybackState.STOPPED
        }
        return try {
            PlaybackState.valueOf(context.data.getString(PLAYBACK_STATE_KEY))
        } catch (_: IllegalArgumentException) {
            PlaybackState.STOPPED
        }
    }

    /**
     * Check if a record is present in the inventory
     */
    private fun hasRecord(context: MovementContext): Boolean {
        val inventory = getInventoryHandler(context) ?: return false
        val record = inventory.getStackInSlot(RecordPlayerBlockEntity.RECORD_SLOT)
        return !record.isEmpty && record.item is EtherealRecordItem
    }

    override fun createVisual(
        visualizationContext: VisualizationContext,
        simulationWorld: VirtualRenderWorld,
        movementContext: MovementContext
    ): ActorVisual {
        return RecordPlayerActorVisual(visualizationContext, simulationWorld, movementContext)
    }

    override fun disableBlockEntityRendering(): Boolean {
        return true
    }

    override fun writeExtraData(context: MovementContext) {
        context.data.putString(PLAYBACK_STATE_KEY, getPlaybackState(context).name)
    }

    private fun startClientPlayer(player: AudioPlayer, audioUrl: String, context: MovementContext) {
        // Calculate offset time for synchronization (same as block entity)
        val offsetSeconds = if (context.blockEntityData.contains(PLAY_TIME_KEY)) {
            val playTime = context.blockEntityData.getLong(PLAY_TIME_KEY)
            if (playTime > 0) {
                (System.currentTimeMillis() - playTime) / 1000.0
            } else {
                0.0
            }
        } else {
            0.0
        }

        val pitchFunction = PitchFunction.smoothedRealTime(
            sourcePitchFunction = PitchFunction.custom { _ ->
                val currSpeed = abs(context.animationSpeed) / 10.0f
                currSpeed.remapTo(0.0f, 700.0f, MIN_PITCH, MAX_PITCH)
            },
            transitionTimeSeconds = 0.5
        )

        player.play(
            audioUrl,
            EffectChain(
                listOf(
                    PitchShiftEffect(pitchFunction),
                )
            ),
            offsetSeconds
        )

    }

    private fun getPlayerUUID(context: MovementContext): UUID? {
        // Read from context.data which is automatically synced between server and client
        if (!context.blockEntityData.contains(PLAYER_UUID_KEY)) {
            Logger.err("PlayerUUID not found at ${context.localPos}")
            return null
        }
        return context.blockEntityData.getUUID(PLAYER_UUID_KEY)
    }

    /**
     * Get the audio URL from the record in the inventory (server-side data)
     */
    private fun getAudioUrl(context: MovementContext): String? {
        val inventory = getInventoryHandler(context) ?: return null
        val record = inventory.getStackInSlot(RecordPlayerBlockEntity.RECORD_SLOT)

        if (record.isEmpty || record.item !is EtherealRecordItem) {
            return null
        }

        return getAudioUrl(record)?.takeIf { it.isNotEmpty() }
    }

    private fun getInventoryHandler(context: MovementContext): RecordPlayerMountedStorage? {
        val storageManager = context.contraption.storage
        val rpInventory = storageManager.allItemStorages.get(context.localPos) as? RecordPlayerMountedStorage
        return rpInventory
    }
}