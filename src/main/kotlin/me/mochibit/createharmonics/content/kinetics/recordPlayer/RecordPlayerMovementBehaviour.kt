package me.mochibit.createharmonics.content.kinetics.recordPlayer

import com.simibubi.create.api.behaviour.movement.MovementBehaviour
import com.simibubi.create.content.contraptions.ControlledContraptionEntity
import com.simibubi.create.content.contraptions.behaviour.MovementContext
import com.simibubi.create.content.contraptions.render.ActorVisual
import com.simibubi.create.content.contraptions.render.ContraptionMatrices
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.api.visualization.VisualizationManager
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.audio.AudioPlayer
import me.mochibit.createharmonics.audio.AudioPlayerRegistry
import me.mochibit.createharmonics.audio.comp.PitchSupplierInterpolated
import me.mochibit.createharmonics.audio.instance.SimpleStreamSoundInstance
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBehaviour.PlaybackState
import me.mochibit.createharmonics.content.records.EtherealRecordItem
import me.mochibit.createharmonics.content.records.EtherealRecordItem.Companion.playFromRecord
import me.mochibit.createharmonics.coroutine.MinecraftClientDispatcher
import me.mochibit.createharmonics.coroutine.launchDelayed
import me.mochibit.createharmonics.extension.onClient
import me.mochibit.createharmonics.extension.onServer
import me.mochibit.createharmonics.network.packet.AudioPlayerContextStopPacket
import me.mochibit.createharmonics.network.packet.setBlockData
import me.mochibit.createharmonics.registry.ModConfigurations
import me.mochibit.createharmonics.registry.ModPackets
import net.createmod.catnip.math.VecHelper
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.ShriekParticleOption
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.Vec3
import net.minecraftforge.network.PacketDistributor
import java.util.UUID
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

class RecordPlayerMovementBehaviour : MovementBehaviour {
    companion object {
        private const val PLAY_TIME_KEY = "PlayTime"
        const val PLAYER_UUID_KEY = "RecordPlayerUUID"
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
        val level = context.world
        level.onServer {
            val contraptionEntity = context.contraption.entity
            if (contraptionEntity is ControlledContraptionEntity) {
                launchDelayed(MinecraftClientDispatcher, .1.seconds) {
                    val contraptionEntity = context.contraption.entity
                    if (!contraptionEntity.isAlive) {
                        stopMovingPlayer(context)
                        stopClientAudio(context)
                    }
                }
            } else {
                stopMovingPlayer(context)
                stopClientAudio(context)
            }
        }
    }

    private fun stopClientAudio(context: MovementContext) {
        val playerUUID = getPlayerUUID(context) ?: return
        ModPackets.channel.send(
            PacketDistributor.ALL.noArg(),
            AudioPlayerContextStopPacket(playerUUID.toString()),
        )
        unregisterPlayer(playerUUID.toString())
        context.data.remove("PendingAudioStop")
    }

    private fun updateServerData(context: MovementContext) {
        // Check if we should skip this update (e.g., just after stopMovingPlayer was called)
        if (context.data.getBoolean(SKIP_NEXT_UPDATE_KEY)) {
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
            if (hasRecord && oldPlayTime == 0L) {
                handleRecordUse(context)
            }
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

    private fun handleRecordUse(context: MovementContext) {
        context.world?.onServer { level ->
            val storage =
                context.contraption.storage.allItemStorages[context.localPos] as? RecordPlayerMountedStorage ?: return
            val record = getRecordItem(context)
            val result = EtherealRecordItem.handleRecordUse(record, RandomSource.create())

            when {
                result.shouldReplace -> {
                    result.replacementStack?.let { storage.setRecord(it) }
                }

                result.isBroken -> {
                    storage.setRecord(ItemStack.EMPTY)
                    val itemStack = (result as EtherealRecordItem.Companion.RecordUseResult.Broken).dropStack.copy()

                    val pos = BlockPos.containing(context.position)
                    val facing = context.state.getValue(BlockStateProperties.FACING)

                    // Transform the facing direction through the contraption's rotation
                    val localDirection = Vec3(facing.stepX.toDouble(), facing.stepY.toDouble(), facing.stepZ.toDouble())
                    val worldDirection = context.rotation.apply(localDirection).normalize()

                    val dropPos = Vec3.atCenterOf(pos).add(worldDirection.scale(0.7))

                    val itemEntity = ItemEntity(level, dropPos.x, dropPos.y, dropPos.z, itemStack)

                    // Launch in the rotated facing direction
                    itemEntity.deltaMovement = worldDirection.scale(0.3)

                    level.addFreshEntity(itemEntity)
                    level.playSound(null, pos, SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, .7f, 1.7f)
                    level.playSound(null, pos, SoundEvents.SMALL_AMETHYST_BUD_BREAK, SoundSource.PLAYERS)
                }
            }
        }
    }

    override fun tick(context: MovementContext) {
        context.world.onServer {
            registerContextIfNotPresent(getPlayerUUID(context).toString(), context)
            updateServerData(context)
        }

        context.world.onClient { _, _ ->
            updateClientState(context)
            handleClientPlayback(context)
        }
    }

    override fun writeExtraData(context: MovementContext) {
        // TODO This doesn't work, PLAY_TIME_KEY must be set in context.temporaryData and taken from there
        context.data.putLong(PLAY_TIME_KEY, context.data.getLong(PLAY_TIME_KEY))
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

    private fun buildPitchSupplier(context: MovementContext): () -> Float {
        if (context.temporaryData !is PitchSupplierInterpolated) {
            context.temporaryData =
                PitchSupplierInterpolated({
                    // Check if speed-based pitch is enabled in client config
                    if (!ModConfigurations.client.enableSpeedBasedPitch.get()) {
                        return@PitchSupplierInterpolated 1.0f
                    }

                    when (context.contraption.entity) {
                        is ControlledContraptionEntity -> {
                            calculateControlledContraptionPitch(context)
                        }

                        else -> {
                            // Fallback to animation speed for other contraption types
                            calculateDefaultPitch(context.animationSpeed)
                        }
                    }
                }, 500)
        }

        return { (context.temporaryData as PitchSupplierInterpolated).getPitch() }
    }

    private fun calculateControlledContraptionPitch(context: MovementContext): Float {
        // Get min and max pitch from config
        val minPitch =
            ModConfigurations.client.minPitch
                .get()
                .toFloat()
        val maxPitch =
            ModConfigurations.client.maxPitch
                .get()
                .toFloat()

        // Use animation speed as a proxy for rotation speed
        val rotationSpeed = abs(context.animationSpeed)

        // Define speed thresholds based on actual train animation speed values
        // Observed values: min ~100, half max ~600, max ~1100
        val maxSpeed = 1200f // Slightly higher than max to leave headroom
        val relativeSpeed = (rotationSpeed / maxSpeed).coerceIn(0f, 1f)

        // Define the curve: rise from 0-25%, plateau 25-75%, rise 75-100%
        val lowerThreshold = 0.25f // Reach pitch 1.0 at 25% speed (~300 animation speed)
        val upperThreshold = 0.75f // Start rising to maxPitch at 75% speed (~900 animation speed)

        return when {
            // Rising phase: 0-25% speed -> minPitch to 1.0
            relativeSpeed < lowerThreshold -> {
                val t = (relativeSpeed / lowerThreshold).coerceIn(0f, 1f)
                minPitch + ((1.0f - minPitch) * t)
            }

            // Plateau phase: 25-75% speed -> stay at 1.0
            relativeSpeed in lowerThreshold..upperThreshold -> {
                1.0f
            }

            // Rising phase: 75-100% speed -> 1.0 to maxPitch
            relativeSpeed > upperThreshold -> {
                val t = ((relativeSpeed - upperThreshold) / (1.0f - upperThreshold)).coerceIn(0f, 1f)
                1.0f + ((maxPitch - 1.0f) * t)
            }

            else -> {
                1.0f
            }
        }
    }

    private fun calculateDefaultPitch(animationSpeed: Float): Float {
        // Get min and max pitch from config
        val minPitch =
            ModConfigurations.client.minPitch
                .get()
                .toFloat()
        val maxPitch =
            ModConfigurations.client.maxPitch
                .get()
                .toFloat()

        val speed = abs(animationSpeed)

        // Define speed thresholds based on actual animation speed values
        // Observed values for trains: min ~100, half max ~600, max ~1100
        val maxSpeed = 1200f // Slightly higher than max to leave headroom
        val relativeSpeed = (speed / maxSpeed).coerceIn(0f, 1f)

        // Define the curve: rise from 0-25%, plateau 25-75%, rise 75-100%
        val lowerThreshold = 0.25f // Reach pitch 1.0 at 25% speed (~300 animation speed)
        val upperThreshold = 0.65f // Start rising to maxPitch at 75% speed (~900 animation speed)

        return when {
            // Rising phase: 0-25% speed -> minPitch to 1.0
            relativeSpeed < lowerThreshold -> {
                val t = (relativeSpeed / lowerThreshold).coerceIn(0f, 1f)
                minPitch + ((1.0f - minPitch) * t)
            }

            // Plateau phase: 25-75% speed -> stay at 1.0
            relativeSpeed in lowerThreshold..upperThreshold -> {
                1.0f
            }

            // Rising phase: 75-100% speed -> 1.0 to maxPitch
            relativeSpeed > upperThreshold -> {
                val t = ((relativeSpeed - upperThreshold) / (1.0f - upperThreshold)).coerceIn(0f, 1f)
                1.0f + ((maxPitch - 1.0f) * t)
            }

            else -> {
                1.0f
            }
        }
    }

    private fun handleClientPlayback(context: MovementContext) {
        val uuid = getPlayerUUID(context) ?: return
        val playerId = uuid.toString()

        val player = getOrCreateAudioPlayer(playerId, context)

        val r = context.world.getRandom()
        val c = context.position
        val v =
            c.add(
                VecHelper
                    .offsetRandomly(Vec3.ZERO, r, .125f)
                    .multiply(1.0, 5.0, 1.0),
            )
        if (r.nextInt(15) == 0) {
            when (player.state) {
                AudioPlayer.PlayState.LOADING -> {
                    context.world.addParticle(
                        ShriekParticleOption(2),
                        false,
                        v.x,
                        v.y,
                        v.z,
                        0.0,
                        12.5,
                        0.0,
                    )
                }

                AudioPlayer.PlayState.PLAYING -> {
                    context.world.addParticle(
                        ParticleTypes.NOTE,
                        v.x,
                        v.y,
                        v.z,
                        context.world.random
                            .nextFloat()
                            .toDouble(),
                        0.0,
                        0.0,
                    )
                }

                else -> {
                }
            }
        }

        if (player.state == AudioPlayer.PlayState.LOADING) {
            return
        }

        // Calculate desired playback state based on current local contraption state
        val hasRecord = context.data.getBoolean(HAS_RECORD_KEY)
        val playTime = context.data.getLong(PLAY_TIME_KEY)
        val currentSpeed = abs(context.animationSpeed)
        val isStalled = context.stall
        val currentTick = context.world.gameTime

        val wasAudioEnded = context.data.contains(AUDIO_ENDED_KEY)
        if (wasAudioEnded) {
            context.data.remove(AUDIO_ENDED_KEY)
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
            if (!context.data.contains(PAUSE_REQUESTED_TICK_KEY)) {
                context.data.putLong(PAUSE_REQUESTED_TICK_KEY, currentTick)
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
                        player.resume()
                    }

                    AudioPlayer.PlayState.STOPPED -> {
                        val record = getRecordItem(context)
                        val offsetSeconds =
                            if (playTime > 0) (System.currentTimeMillis() - playTime) / 1000.0 else 0.0

                        player.playFromRecord(record, offsetSeconds) {
                            val pitchFunction =
                                context.temporaryData as? PitchSupplierInterpolated ?: return@playFromRecord 1f
                            pitchFunction.getPitch()
                        }
                    }

                    AudioPlayer.PlayState.PLAYING, AudioPlayer.PlayState.LOADING -> {
                        // Already in correct state, do nothing
                    }
                }
            }

            PlaybackState.PAUSED -> {
                if (player.state == AudioPlayer.PlayState.PLAYING) {
                    player.pause()
                }
            }

            PlaybackState.STOPPED -> {
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
    ): AudioPlayer {
        // Check if we've already initialized the player for this context
        val playerInitializedKey = "PlayerInitialized"
        val isInitialized = context.data.getBoolean(playerInitializedKey)
        val playerExists = AudioPlayerRegistry.containsStream(playerId)

        // Need to initialize if: not initialized yet, OR initialized but player was destroyed
        if (!isInitialized || !playerExists) {
            // Clean up any stale player (shouldn't exist, but just in case)
            if (playerExists) {
                val existingPlayer = AudioPlayerRegistry.getPlayer(playerId)
                existingPlayer?.stopSoundImmediately()
                AudioPlayerRegistry.destroyPlayer(playerId)
            }

            // Reset play time and other state when creating a fresh player
            context.data.putLong(PLAY_TIME_KEY, 0)
            context.data.remove(AUDIO_ENDED_KEY)
            context.data.remove(PAUSE_REQUESTED_TICK_KEY)

            context.data.putBoolean(playerInitializedKey, true)
        }

        return AudioPlayerRegistry.getOrCreatePlayer(playerId) {
            AudioPlayer(
                { streamId, stream ->
                    SimpleStreamSoundInstance(
                        stream,
                        streamId,
                        SoundEvents.EMPTY,
                        posSupplier = { BlockPos.containing(context.position) },
                        pitchSupplier = buildPitchSupplier(context),
                        radiusSupplier = { 16 },
                    )
                },
                playerId,
            )
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

    override fun renderInContraption(
        context: MovementContext,
        renderWorld: VirtualRenderWorld,
        matrices: ContraptionMatrices,
        buffer: MultiBufferSource,
    ) {
        if (VisualizationManager.supportsVisualization(context.world)) return
        RecordPlayerRenderer.renderInContraption(context, renderWorld, matrices, buffer)
    }

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
}
