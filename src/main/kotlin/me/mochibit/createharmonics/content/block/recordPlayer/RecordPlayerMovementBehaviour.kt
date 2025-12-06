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
        private const val PLAYBACK_STATE_KEY = "PlaybackState"
        private const val PLAY_TIME_KEY = "PlayTime"
        private const val PLAYER_UUID_KEY = "RecordPlayerUUID"
        private const val CLIENT_LAST_PAUSED_TIME_KEY = "ClientLastPausedTime"
        private const val CLIENT_RESUME_GRACE_PERIOD_MS = 500L // Client-side grace period before resuming
        private const val SERVER_STATE_CHANGE_DEBOUNCE_TICKS = 10 // Server debounce period in ticks
        private const val LAST_STATE_CHANGE_TICK_KEY = "LastStateChangeTick"
        private const val SPEED_THRESHOLD = 0.01f // Minimum speed to consider as "moving"
    }

    override fun stopMoving(context: MovementContext) {
        context.world.onServer {
            ModNetworkHandler.channel.send(
                PacketDistributor.ALL.noArg(),
                AudioPlayerContextStopPacket(getPlayerUUID(context).toString()),
            )
        }
    }

    private fun updateServerPlaybackState(context: MovementContext) {
        val currentSpeed = abs(context.animationSpeed)
        val hasRecord = hasRecord(context)
        val currentState = getPlaybackState(context)
        val isStalled = context.stall

        // Get current tick from world
        val currentTick = context.world.gameTime
        val lastStateChangeTick = context.data.getLong(LAST_STATE_CHANGE_TICK_KEY)

        // Check if we're still in debounce period (except for STOPPED state)
        val ticksSinceLastChange = currentTick - lastStateChangeTick
        if (currentState != PlaybackState.STOPPED && ticksSinceLastChange < SERVER_STATE_CHANGE_DEBOUNCE_TICKS) {
            return
        }

        val newState =
            when {
                // No record, should be stopped (immediate)
                !hasRecord -> {
                    PlaybackState.STOPPED
                }

                // If stalled, keep playing regardless of speed
                isStalled -> {
                    PlaybackState.PLAYING
                }

                // Speed is effectively zero and not stalled, pause if currently playing
                currentSpeed < SPEED_THRESHOLD -> {
                    if (currentState == PlaybackState.PLAYING) {
                        PlaybackState.PAUSED
                    } else {
                        currentState
                    }
                }

                // Speed is positive, resume playing if paused or stopped
                currentSpeed >= SPEED_THRESHOLD -> {
                    when (currentState) {
                        PlaybackState.PAUSED, PlaybackState.STOPPED -> PlaybackState.PLAYING
                        else -> currentState
                    }
                }

                else -> {
                    currentState
                }
            }

        if (newState != currentState) {
            Logger.info(
                "MovementBehaviour state change: $currentState -> $newState " +
                    "(speed: $currentSpeed, stalled: $isStalled, ticks since last: $ticksSinceLastChange)",
            )
            context.data.putLong(LAST_STATE_CHANGE_TICK_KEY, currentTick)
            updatePlaybackState(context, newState)
        }
    }

    override fun tick(context: MovementContext) {
        context.world.onServer {
            updateServerPlaybackState(context)
        }

        context.world.onClient {
            updateClientState(context)
            handleClientPlayback(context)
        }
    }

    private fun updateClientState(context: MovementContext) {
        val blockNbt = context.contraption.blocks[context.localPos]?.nbt ?: return

        val newPlaybackState = blockNbt.getString(PLAYBACK_STATE_KEY)
        val newPlayTime = blockNbt.getLong(PLAY_TIME_KEY)

        // Update playback state if changed
        if (newPlaybackState.isNotEmpty()) {
            val currentState = context.data.getString(PLAYBACK_STATE_KEY)
            if (currentState != newPlaybackState) {
                context.data.putString(PLAYBACK_STATE_KEY, newPlaybackState)
            }
        }

        // Update play time if changed
        val currentPlayTime = context.data.getLong(PLAY_TIME_KEY)
        if (currentPlayTime != newPlayTime) {
            context.data.putLong(PLAY_TIME_KEY, newPlayTime)
        }
    }

    private fun handleClientPlayback(context: MovementContext) {
        val playbackState = getPlaybackState(context)
        val playTime = context.data.getLong(PLAY_TIME_KEY)

        val uuid = getPlayerUUID(context) ?: return
        val playerId = uuid.toString()
        val record = getRecordItemClient(context)
        val audioUrl = getAudioUrl(record) ?: return

        val player = getOrCreateAudioPlayer(playerId, context)
        val currentTime = System.currentTimeMillis()

        when (playbackState) {
            PlaybackState.PLAYING -> {
                // Check if we should actually resume from pause (client-side debouncing)
                if (player.state == AudioPlayer.PlayState.PAUSED) {
                    // Check when we were paused
                    val lastPausedTime = context.data.getLong(CLIENT_LAST_PAUSED_TIME_KEY)
                    if (lastPausedTime > 0) {
                        val timeSincePause = currentTime - lastPausedTime
                        if (timeSincePause < CLIENT_RESUME_GRACE_PERIOD_MS) {
                            // Still in grace period, don't resume yet
                            Logger.info("Client debounce: ignoring resume (${timeSincePause}ms since pause)")
                            return
                        }
                    }
                    // Clear the paused time since we're resuming
                    context.data.remove(CLIENT_LAST_PAUSED_TIME_KEY)
                    Logger.info("Client: resuming playback after debounce")
                }

                handlePlayingState(player, context, audioUrl, playTime)
            }

            PlaybackState.PAUSED -> {
                // Track when we first received the paused state
                if (!context.data.contains(CLIENT_LAST_PAUSED_TIME_KEY)) {
                    context.data.putLong(CLIENT_LAST_PAUSED_TIME_KEY, currentTime)
                    Logger.info("Client: entering pause state, starting grace period")
                }

                if (player.state != AudioPlayer.PlayState.PAUSED) {
                    player.pause()
                }
            }

            PlaybackState.STOPPED -> {
                // Clear any pause tracking
                context.data.remove(CLIENT_LAST_PAUSED_TIME_KEY)

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

    private fun handlePlayingState(
        player: AudioPlayer,
        @Suppress("UNUSED_PARAMETER")
        context: MovementContext,
        audioUrl: String,
        playTime: Long,
    ) {
        when (player.state) {
            AudioPlayer.PlayState.PAUSED -> {
                player.resume()
            }

            AudioPlayer.PlayState.PLAYING, AudioPlayer.PlayState.LOADING -> {
                return
            }

            // Already playing or loading, do nothing
            AudioPlayer.PlayState.STOPPED -> {
                // Start playback
                val offsetSeconds = if (playTime > 0) (System.currentTimeMillis() - playTime) / 1000.0 else 0.0
                player.play(audioUrl, EffectChain.empty(), offsetSeconds)
            }
        }
    }

    private fun updatePlaybackState(
        context: MovementContext,
        newState: PlaybackState,
    ) {
        val oldState = getPlaybackState(context)

        // Update context data
        context.data.putString(PLAYBACK_STATE_KEY, newState.name)

        // Update play time based on state transition
        when {
            newState == PlaybackState.STOPPED -> {
                context.data.putLong(PLAY_TIME_KEY, 0)
            }

            newState == PlaybackState.PLAYING && oldState != PlaybackState.PLAYING -> {
                context.data.putLong(PLAY_TIME_KEY, System.currentTimeMillis())
            }
        }

        // Sync to contraption block data for client synchronization
        val block = context.contraption.blocks[context.localPos] ?: return
        val nbt = block.nbt ?: return
        nbt.putString(PLAYBACK_STATE_KEY, newState.name)
        nbt.putLong(PLAY_TIME_KEY, context.data.getLong(PLAY_TIME_KEY))
        context.contraption.entity.setBlockData(context.localPos, block)
    }

    private fun getPlaybackState(context: MovementContext): PlaybackState {
        val nbt = context.data

        if (!nbt.contains(PLAYBACK_STATE_KEY)) {
            return PlaybackState.STOPPED
        }
        return try {
            PlaybackState.valueOf(nbt.getString(PLAYBACK_STATE_KEY))
        } catch (_: IllegalArgumentException) {
            PlaybackState.STOPPED
        }
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
