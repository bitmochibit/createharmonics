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
import me.mochibit.createharmonics.audio.instance.MovingSoundInstance
import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerBehaviour.PlaybackState
import me.mochibit.createharmonics.content.item.EtherealRecordItem
import me.mochibit.createharmonics.content.item.EtherealRecordItem.Companion.getAudioUrl
import me.mochibit.createharmonics.extension.onClient
import me.mochibit.createharmonics.extension.onServer
import me.mochibit.createharmonics.network.ModNetworkHandler
import me.mochibit.createharmonics.network.packet.AudioPlayerContextStopPacket
import me.mochibit.createharmonics.network.packet.setBlockData
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraftforge.network.PacketDistributor
import java.util.*
import kotlin.math.abs

daskldjsadlksaj

class RecordPlayerMovementBehaviour : MovementBehaviour {
    companion object {
        private const val PLAY_TIME_KEY = "PlayTime"
        private const val PLAYER_UUID_KEY = "RecordPlayerUUID"
        private const val SPEED_THRESHOLD = 0.01f // Minimum speed to consider as "moving"
        private const val HAS_RECORD_KEY = "HasRecord"
        private const val PAUSE_GRACE_PERIOD_TICKS = 5 // Wait 5 ticks (0.25s) before actually pausing
        private const val PAUSE_REQUESTED_TICK_KEY = "PauseRequestedTick"
        private const val AUDIO_ENDED_KEY = "AudioEnded"
        private const val SKIP_NEXT_UPDATE_KEY =
            "SkipNextUpdate" // Server-side flag to prevent overwriting play time reset

        private val activePlayerContextByUUID = mutableMapOf<String, MovementContext>()

        fun getContextByPlayerUUID(playerUUID: String): MovementContext? = activePlayerContextByUUID[playerUUID]

        private fun registerContextIfNotPresent(
            playerUUID: String,
            context: MovementContext,
        ) {
            activePlayerContextByUUID.putIfAbsent(playerUUID, context)
        }

        fun unregisterPlayer(playerUUID: String) {
            activePlayerContextByUUID.remove(playerUUID)
        }

        /**
         * Stops playback on a moving record player by resetting the play time.
         * This is called when a stream ends naturally and needs to be synchronized to all clients.
         * After stopping, the player will automatically restart on the next tick if conditions are met (has record and moving).
         */
        fun stopMovingPlayer(context: MovementContext) {
            Logger.info("Server: Stopping moving player (audio ended), resetting play time to 0")

            // Reset play time to 0 in context.data
            context.data.putLong(PLAY_TIME_KEY, 0)

            // Set flag to prevent updateServerData from overwriting this on the next tick
            context.data.putBoolean(SKIP_NEXT_UPDATE_KEY, true)

            // Also update the block NBT to sync to clients
            val block = context.contraption.blocks[context.localPos]
            val nbt = block?.nbt
            if (block != null && nbt != null) {
                nbt.putLong(PLAY_TIME_KEY, 0)
                // Keep HAS_RECORD_KEY as true if there's still a record
                // This will be updated on next server tick anyway
                context.contraption.entity.setBlockData(context.localPos, block)
            }
        }
    }

    override fun stopMoving(context: MovementContext) {
        // STOP MOVING ONLY WORKS ON SERVER LEVEL!
        // This is called when the contraption stops moving, including when bearing speed changes.
        // We send a packet to destroy the audio player on all clients so they can restart fresh.
        context.world.onServer {
            val playerId = getPlayerUUID(context).toString()
            ModNetworkHandler.channel.send(
                PacketDistributor.ALL.noArg(),
                AudioPlayerContextStopPacket(playerId),
            )
            unregisterPlayer(playerId)
        }
    }

    private fun updateServerData(context: MovementContext) {
        // Check if we should skip this update (e.g., just after stopMovingPlayer was called)
        if (context.data.getBoolean(SKIP_NEXT_UPDATE_KEY)) {
            Logger.info("Server: Skipping update to preserve play time reset")
            context.data.remove(SKIP_NEXT_UPDATE_KEY)
            return
        }

        val hasRecord = hasRecord(context)
        val currentSpeed = abs(context.animationSpeed)
        val isStalled = context.stall

        // Determine if contraption should be "playing" (has record and is moving or stalled)
        val shouldBePlaying = hasRecord && (currentSpeed >= SPEED_THRESHOLD || isStalled)

        // Track old values to detect changes
        val oldPlayTime = context.data.getLong(PLAY_TIME_KEY)
        val oldHasRecord = context.data.getBoolean(HAS_RECORD_KEY)

        var dataChanged = false

        // Update playTime continuously while playing
        val newPlayTime =
            if (shouldBePlaying) {
                System.currentTimeMillis()
            } else if (!hasRecord) {
                // Only reset playTime when record is removed
                // Keep playTime alive during temporary stops (e.g., mining interruptions)
                0L
            } else {
                oldPlayTime // Keep existing playTime
            }

        // Check if data actually changed
        if (oldPlayTime != newPlayTime) {
            context.data.putLong(PLAY_TIME_KEY, newPlayTime)
            dataChanged = true
        }

        if (oldHasRecord != hasRecord) {
            context.data.putBoolean(HAS_RECORD_KEY, hasRecord)
            dataChanged = true
        }

        // Only sync to contraption block data if something changed
        if (dataChanged) {
            val block = context.contraption.blocks[context.localPos]
            val nbt = block?.nbt
            if (block != null && nbt != null) {
                nbt.putLong(PLAY_TIME_KEY, newPlayTime)
                nbt.putBoolean(HAS_RECORD_KEY, hasRecord)
                context.contraption.entity.setBlockData(context.localPos, block)
            }
        }
    }

    override fun tick(context: MovementContext) {
        context.world.onServer {
            registerContextIfNotPresent(getPlayerUUID(context).toString(), context)
            updateServerData(context)
        }

        context.world.onClient {
            updateClientState(context)
            handleClientPlayback(context)
        }
    }

    private fun updateClientState(context: MovementContext) {
        val blockNbt = context.contraption.blocks[context.localPos]?.nbt ?: return

        val newPlayTime = blockNbt.getLong(PLAY_TIME_KEY)
        val newHasRecord = blockNbt.getBoolean(HAS_RECORD_KEY)

        // Get current values BEFORE updating
        val currentPlayTime = context.data.getLong(PLAY_TIME_KEY)
        val currentHasRecord = context.data.getBoolean(HAS_RECORD_KEY)

        // Detect if play time was reset to 0 (indicates audio ended and should restart)
        // This happens when the server receives the stream end packet
        if (currentPlayTime > 0 && newPlayTime == 0L && newHasRecord) {
            // Mark that audio ended so we can trigger a clean restart
            context.data.putBoolean(AUDIO_ENDED_KEY, true)
            Logger.info("Client: Detected audio end (playTime reset: $currentPlayTime -> $newPlayTime)")
        }

        // Update play time if changed
        if (currentPlayTime != newPlayTime) {
            context.data.putLong(PLAY_TIME_KEY, newPlayTime)
        }

        // Update hasRecord if changed
        if (currentHasRecord != newHasRecord) {
            context.data.putBoolean(HAS_RECORD_KEY, newHasRecord)
        }
    }

    private fun handleClientPlayback(context: MovementContext) {
        val uuid = getPlayerUUID(context) ?: return
        val playerId = uuid.toString()

        val player = getOrCreateAudioPlayer(playerId, context)

        if (player.state == AudioPlayer.PlayState.LOADING) {
            return
        }

        // Calculate desired playback state based on current local contraption state
        val hasRecord = context.data.getBoolean(HAS_RECORD_KEY)
        val playTime = context.data.getLong(PLAY_TIME_KEY)
        val currentSpeed = abs(context.animationSpeed)
        val isStalled = context.stall
        val currentTick = context.world.gameTime

        // Check if audio was stopped externally (e.g., by stopMoving() when bearing speed changes)
        val wasAudioEnded = context.data.contains(AUDIO_ENDED_KEY)
        if (wasAudioEnded) {
            context.data.remove(AUDIO_ENDED_KEY)
            Logger.info("Client: Audio ended externally, will force restart if conditions met")
        }

        // Determine raw desired state (before grace period)
        val rawDesiredState =
            when {
                !hasRecord -> {
                    PlaybackState.STOPPED
                }

                currentSpeed >= SPEED_THRESHOLD || isStalled -> {
                    PlaybackState.PLAYING
                }

                else -> {
                    PlaybackState.PAUSED
                }
            }

        // Grace period logic for PAUSED state to avoid momentary interruptions
        val desiredState: PlaybackState

        if (rawDesiredState == PlaybackState.PAUSED) {
            // We want to pause, but we were playing - check grace period
            if (!context.data.contains(PAUSE_REQUESTED_TICK_KEY)) {
                // First tick we want to pause - record the tick
                context.data.putLong(PAUSE_REQUESTED_TICK_KEY, currentTick)
                Logger.info("Client: pause requested, starting grace period (speed: $currentSpeed")
                // Keep playing for now
                return
            } else {
                // We've been wanting to pause for a while - check if grace period expired
                val pauseRequestedTick = context.data.getLong(PAUSE_REQUESTED_TICK_KEY)
                val ticksSincePauseRequested = currentTick - pauseRequestedTick

                if (ticksSincePauseRequested < PAUSE_GRACE_PERIOD_TICKS) {
                    // Still in grace period - keep playing
                    return
                } else {
                    // Grace period expired - actually pause now
                    desiredState = PlaybackState.PAUSED
                    context.data.remove(PAUSE_REQUESTED_TICK_KEY)
                }
            }
        } else {
            // Not trying to pause, or already paused/stopped - clear grace period
            context.data.remove(PAUSE_REQUESTED_TICK_KEY)
            desiredState = rawDesiredState
        }

        when (desiredState) {
            PlaybackState.PLAYING -> {
                when (player.state) {
                    AudioPlayer.PlayState.PAUSED -> {
                        Logger.info("Client: resuming playback")
                        player.resume()
                    }

                    AudioPlayer.PlayState.STOPPED -> {
                        // Get audio URL only when we need to start playback
                        val record = getRecordItemClient(context)
                        val audioUrl = getAudioUrl(record)

                        if (audioUrl != null) {
                            // Start playback with offset
                            val offsetSeconds =
                                if (playTime > 0) (System.currentTimeMillis() - playTime) / 1000.0 else 0.0
                            Logger.info("Client: starting playback with offset ${offsetSeconds}s")
                            player.play(audioUrl, EffectChain.empty(), offsetSeconds)
                        } else {
                            Logger.warn("Client: Cannot start playback, no valid audio URL")
                        }
                    }

                    AudioPlayer.PlayState.PLAYING, AudioPlayer.PlayState.LOADING -> {
                        // Already in correct state, do nothing
                    }
                }
            }

            PlaybackState.PAUSED -> {
                Logger.info("Client: pausing playback")
                // Only pause if actually playing
                if (player.state == AudioPlayer.PlayState.PLAYING) {
                    player.pause()
                }
            }

            PlaybackState.STOPPED -> {
                if (player.state != AudioPlayer.PlayState.STOPPED) {
                    Logger.info("Client: stopping playback")
                    player.stop()
                }
            }

            else -> {}
        }
    }

    private fun getOrCreateAudioPlayer(
        playerId: String,
        context: MovementContext,
    ): AudioPlayer =
        AudioPlayerRegistry.getOrCreatePlayer(playerId) {
            Logger.info("Creating audio player for moving contraption: $playerId at ${context.localPos}")
            AudioPlayer(
                { streamId, stream ->
                    MovingSoundInstance(
                        stream,
                        streamId,
                        posSupplier = { BlockPos.containing(context.position) },
                    )
                },
                playerId,
            )
        }

    private fun hasRecord(context: MovementContext): Boolean {
        val record = getRecordItem(context)
        return !record.isEmpty && record.item is EtherealRecordItem
    }

    override fun createVisual(
        visualizationContext: VisualizationContext,
        simulationWorld: VirtualRenderWorld,
        movementContext: MovementContext,
    ): ActorVisual = RecordPlayerActorVisual(visualizationContext, simulationWorld, movementContext)

    override fun disableBlockEntityRendering(): Boolean = true

    private fun getPlayerUUID(context: MovementContext): UUID? {
        if (!context.blockEntityData.contains(PLAYER_UUID_KEY)) {
            Logger.err("PlayerUUID not found at ${context.localPos}")
            return null
        }
        return context.blockEntityData.getUUID(PLAYER_UUID_KEY)
    }

    private fun getRecordItem(context: MovementContext): ItemStack {
        val handler =
            context.contraption.storage.allItemStorages[context.localPos] as? RecordPlayerMountedStorage
                ?: return ItemStack.EMPTY
        return handler.getRecord()
    }

    private fun getRecordItemClient(context: MovementContext): ItemStack {
        val be =
            context.contraption.presentBlockEntities[context.localPos] as? RecordPlayerBlockEntity
                ?: return ItemStack.EMPTY
        val record: ItemStack = be.getRecord()
        return record
    }
}
