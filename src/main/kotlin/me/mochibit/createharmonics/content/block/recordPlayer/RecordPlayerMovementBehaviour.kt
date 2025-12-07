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

class RecordPlayerMovementBehaviour : MovementBehaviour {
    companion object {
        private const val PLAY_TIME_KEY = "PlayTime"
        private const val PLAYER_UUID_KEY = "RecordPlayerUUID"
        private const val CLIENT_LAST_PLAYER_STATE_KEY = "ClientLastPlayerState"
        private const val SPEED_THRESHOLD = 0.01f // Minimum speed to consider as "moving"
        private const val HAS_RECORD_KEY = "HasRecord"
        private const val PAUSE_GRACE_PERIOD_TICKS = 5 // Wait 5 ticks (0.25s) before actually pausing
        private const val PAUSE_REQUESTED_TICK_KEY = "PauseRequestedTick"
    }

    override fun stopMoving(context: MovementContext) {
        context.world.onServer {
            ModNetworkHandler.channel.send(
                PacketDistributor.ALL.noArg(),
                AudioPlayerContextStopPacket(getPlayerUUID(context).toString()),
            )
        }

        context.world.onClient {
            // Clean up client state tracking
            context.data.remove(CLIENT_LAST_PLAYER_STATE_KEY)
            context.data.remove(PAUSE_REQUESTED_TICK_KEY)
        }
    }

    private fun updateServerData(context: MovementContext) {
        val hasRecord = hasRecord(context)
        val currentSpeed = abs(context.animationSpeed)
        val isStalled = context.stall

        // Determine if contraption should be "playing" (has record and is moving or stalled)
        val shouldBePlaying = hasRecord && (currentSpeed >= SPEED_THRESHOLD || isStalled)

        // Update playTime continuously while playing
        if (shouldBePlaying) {
            val newPlayTime = System.currentTimeMillis()
            context.data.putLong(PLAY_TIME_KEY, newPlayTime)
        } else if (!hasRecord) {
            // Only reset playTime when record is removed
            // Keep playTime alive during temporary stops (e.g., mining interruptions)
            context.data.putLong(PLAY_TIME_KEY, 0)
        }
        // Note: If hasRecord but not playing (paused), we keep the old playTime
        // so the track position is preserved for seamless resume

        // Track hasRecord state for client sync
        context.data.putBoolean(HAS_RECORD_KEY, hasRecord)

        // Sync to contraption block data for client
        val block = context.contraption.blocks[context.localPos]
        val nbt = block?.nbt
        if (block != null && nbt != null) {
            nbt.putLong(PLAY_TIME_KEY, context.data.getLong(PLAY_TIME_KEY))
            nbt.putBoolean(HAS_RECORD_KEY, hasRecord)
            context.contraption.entity.setBlockData(context.localPos, block)
        }
    }

    override fun tick(context: MovementContext) {
        context.world.onServer {
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

        // Update play time if changed
        val currentPlayTime = context.data.getLong(PLAY_TIME_KEY)
        if (currentPlayTime != newPlayTime) {
            context.data.putLong(PLAY_TIME_KEY, newPlayTime)
        }

        // Update hasRecord if changed
        val currentHasRecord = context.data.getBoolean(HAS_RECORD_KEY)
        if (currentHasRecord != newHasRecord) {
            context.data.putBoolean(HAS_RECORD_KEY, newHasRecord)
        }
    }

    private fun handleClientPlayback(context: MovementContext) {
        val uuid = getPlayerUUID(context) ?: return
        val playerId = uuid.toString()

        val player = getOrCreateAudioPlayer(playerId, context)

        // CRITICAL: Never send commands to a player that's still loading
        // This prevents corrupted audio from rapid state changes during initialization
        if (player.state == AudioPlayer.PlayState.LOADING) {
            return
        }

        // Calculate desired playback state based on current local contraption state
        val hasRecord = context.data.getBoolean(HAS_RECORD_KEY)
        val playTime = context.data.getLong(PLAY_TIME_KEY)
        val currentSpeed = abs(context.animationSpeed)
        val isStalled = context.stall
        val currentTick = context.world.gameTime

        // Determine raw desired state (before grace period)
        val rawDesiredState =
            when {
                !hasRecord -> PlaybackState.STOPPED
                currentSpeed >= SPEED_THRESHOLD || isStalled -> PlaybackState.PLAYING
                else -> PlaybackState.PAUSED
            }

        // Get the last state we processed
        val lastPlaybackState =
            if (context.data.contains(CLIENT_LAST_PLAYER_STATE_KEY)) {
                context.data.getString(CLIENT_LAST_PLAYER_STATE_KEY)
            } else {
                null
            }

        // Grace period logic for PAUSED state to avoid momentary interruptions
        val desiredState: PlaybackState
        val desiredStateStr: String

        if (rawDesiredState == PlaybackState.PAUSED && lastPlaybackState == "PLAYING") {
            // We want to pause, but we were playing - check grace period
            if (!context.data.contains(PAUSE_REQUESTED_TICK_KEY)) {
                // First tick we want to pause - record the tick
                context.data.putLong(PAUSE_REQUESTED_TICK_KEY, currentTick)
                Logger.info("Client: pause requested, starting grace period (speed: $currentSpeed, stalled: $isStalled)")
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
                    desiredStateStr = desiredState.name
                    context.data.remove(PAUSE_REQUESTED_TICK_KEY)
                }
            }
        } else {
            // Not trying to pause, or already paused/stopped - clear grace period
            context.data.remove(PAUSE_REQUESTED_TICK_KEY)
            desiredState = rawDesiredState
            desiredStateStr = desiredState.name
        }

        // Only process if state has actually changed from what we last processed
        if (lastPlaybackState == desiredStateStr) {
            return
        }

        // Update the last processed state immediately to prevent duplicate commands
        context.data.putString(CLIENT_LAST_PLAYER_STATE_KEY, desiredStateStr)

        Logger.info("Client: state transition $lastPlaybackState -> $desiredStateStr (speed: $currentSpeed, stalled: $isStalled)")

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
                Logger.info("Client: stopping playback")

                if (player.state != AudioPlayer.PlayState.STOPPED) {
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
            context.contraption.storage.allItemStorages
                .get(context.localPos) as? RecordPlayerMountedStorage ?: return ItemStack.EMPTY
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
