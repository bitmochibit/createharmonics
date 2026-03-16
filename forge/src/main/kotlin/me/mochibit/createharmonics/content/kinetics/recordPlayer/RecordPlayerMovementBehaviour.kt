package me.mochibit.createharmonics.content.kinetics.recordPlayer

import com.simibubi.create.api.behaviour.movement.MovementBehaviour
import com.simibubi.create.content.contraptions.ControlledContraptionEntity
import com.simibubi.create.content.contraptions.behaviour.MovementContext
import com.simibubi.create.content.contraptions.render.ActorVisual
import com.simibubi.create.content.contraptions.render.ContraptionMatrices
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.api.visualization.VisualizationManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import me.mochibit.createharmonics.audio.AudioPlayerManager
import me.mochibit.createharmonics.audio.effect.EffectPreset
import me.mochibit.createharmonics.audio.effect.PitchShiftEffect
import me.mochibit.createharmonics.audio.instance.SimpleStreamSoundInstance
import me.mochibit.createharmonics.audio.player.AudioPlayer
import me.mochibit.createharmonics.audio.player.PlayerState
import me.mochibit.createharmonics.audio.player.PlaytimeClock
import me.mochibit.createharmonics.audio.player.putClock
import me.mochibit.createharmonics.audio.player.updateClock
import me.mochibit.createharmonics.config.ServerConfig
import me.mochibit.createharmonics.content.record.EtherealRecordItem
import me.mochibit.createharmonics.content.records.RecordUtilities
import me.mochibit.createharmonics.content.records.RecordUtilities.playFromRecord
import me.mochibit.createharmonics.foundation.async.modLaunch
import me.mochibit.createharmonics.foundation.async.thenLaunch
import me.mochibit.createharmonics.foundation.behaviour.movement.setBlockData
import me.mochibit.createharmonics.foundation.err
import me.mochibit.createharmonics.foundation.extension.onClient
import me.mochibit.createharmonics.foundation.extension.onServer
import me.mochibit.createharmonics.foundation.extension.remapTo
import me.mochibit.createharmonics.foundation.extension.ticks
import me.mochibit.createharmonics.foundation.network.packet.AudioPlayerContextStopPacket
import me.mochibit.createharmonics.foundation.registry.ModConfigurations
import me.mochibit.createharmonics.foundation.registry.ModPackets
import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplierInterpolated
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
import java.util.UUID
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

// TODO: This thing needs a refactor and cleanup, it's not scalable enough for future features
class RecordPlayerMovementBehaviour : MovementBehaviour {
    /**
     * Data class to hold temporary context data that persists during runtime.
     * This is stored in context.temporaryData on both client and server.
     * Only playTime and hasRecord are persisted to context.data for saves/loads.
     * Other fields are runtime-only flags that don't need persistence.
     */
    data class RecordPlayerContextData(
        var pitchSupplier: FloatSupplierInterpolated? = null,
        var volumeSupplier: FloatSupplierInterpolated? = null,
        var radiusSupplier: FloatSupplierInterpolated? = null,
        val playtimeClock: PlaytimeClock = PlaytimeClock(),
        var hasRecord: Boolean = false,
        var isPaused: Boolean = false, // Track if currently paused
        // Runtime flags (not persisted to disk)
        var audioEnded: Boolean = false,
        var pauseRequestedTick: Long = -1L,
        var playerInitialized: Boolean = false,
        var skipNextUpdate: Boolean = false,
        var lastSyncTick: Long = 0L, // Track when we last synced to force periodic updates
        // Interpolated suppliers for underwater filter effect
        // Start with "no filter" values (very high cutoff, flat resonance)
        var targetCutoffFrequency: Float = 20000f,
        var targetResonance: Float = 0.707f,
        var cutoffFrequencyInterpolated: FloatSupplierInterpolated? = null,
        var resonanceInterpolated: FloatSupplierInterpolated? = null,
        val underwaterFilter: EffectPreset.UnderwaterFilter = EffectPreset.UnderwaterFilter(),
        var particleJob: Job? = null,
    )

    companion object {
        const val PLAYER_UUID_KEY = "RecordPlayerUUID"
        private const val SPEED_THRESHOLD = 0.01f // Minimum speed to consider as "moving"
        private const val HAS_RECORD_KEY = "HasRecord"
        private const val PAUSE_GRACE_PERIOD_TICKS = 5 // Wait 5 ticks (0.25s) before actually pausing

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
            // Get or create temporaryData
            val tempData = context.temporaryData as? RecordPlayerContextData

            // Reset the clock
            tempData?.playtimeClock?.stop()

            // Set flag to prevent updateServerData from overwriting this on the next tick
            tempData?.skipNextUpdate = true

            // Also update the block NBT to sync to clients
            val block = context.contraption.blocks[context.localPos]
            val nbt = block?.nbt
            if (block != null && nbt != null) {
                if (tempData != null) nbt.putClock(tempData.playtimeClock)
                // Keep HAS_RECORD_KEY as true if there's still a record
                // This will be updated on next server tick anyway
                context.contraption.entity.setBlockData(context.localPos, block)
            }
        }

        fun isPauseModeWithRedstone(context: MovementContext): Boolean {
            val playbackMode =
                RecordPlayerBlockEntity.PlaybackMode.entries[context.blockEntityData.getInt("ScrollValue")]
            val isPauseMode =
                playbackMode == RecordPlayerBlockEntity.PlaybackMode.PAUSE ||
                    playbackMode == RecordPlayerBlockEntity.PlaybackMode.PAUSE_STATIC_PITCH

            val isPowered = context.blockEntityData.getInt("RedstonePower") > 0
            return isPauseMode && isPowered
        }
    }

    override fun stopMoving(context: MovementContext) {
        val level = context.world
        level.onServer {
            val contraptionEntity = context.contraption.entity
            if (contraptionEntity is ControlledContraptionEntity) {
                .1.seconds.thenLaunch {
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
        ModPackets.broadcast(AudioPlayerContextStopPacket(playerUUID.toString()))
        unregisterPlayer(playerUUID.toString())
    }

    private fun updateServerData(context: MovementContext) {
        // Get or create temporaryData
        val tempData = getOrCreateTemporaryData(context)

        // Check if we should skip this update (e.g., just after stopMovingPlayer was called)
        if (tempData.skipNextUpdate) {
            tempData.skipNextUpdate = false
            return
        }

        val hasRecord = hasRecord(context)
        val currentSpeed = abs(context.animationSpeed)
        val isStalled = context.stall

        // Determine if contraption should be "playing" (has record and is moving or stalled)
        val shouldBePlaying =
            !context.disabled && hasRecord && (
                currentSpeed >= SPEED_THRESHOLD || isStalled ||
                    isPauseModeWithRedstone(context)
            )
        val shouldBePaused = hasRecord && !shouldBePlaying

        // Track old values to detect changes
        val oldIsPlaying = tempData.playtimeClock.isPlaying
        val oldHasRecord = tempData.hasRecord

        var dataChanged = false

        // Drive the clock
        when {
            !hasRecord -> {
                if (oldIsPlaying || tempData.isPaused) {
                    tempData.playtimeClock.stop()
                    tempData.isPaused = false
                    dataChanged = true
                }
            }

            shouldBePlaying -> {
                if (!oldIsPlaying) {
                    if (shouldBePaused.not() && tempData.isPaused) {
                        // Resume from pause
                        tempData.playtimeClock.play()
                        tempData.isPaused = false
                    } else {
                        // Fresh start
                        tempData.playtimeClock.play()
                        handleRecordUse(context)
                    }
                    dataChanged = true
                }
            }

            shouldBePaused -> {
                if (oldIsPlaying) {
                    tempData.playtimeClock.pause()
                    tempData.isPaused = true
                    dataChanged = true
                }
            }
        }

        if (oldHasRecord != hasRecord) {
            tempData.hasRecord = hasRecord
            dataChanged = true
        }

        // Force periodic sync even if nothing changed locally (for rejoining clients)
        // Sync at least every 20 ticks (1 second) when playing
        val currentTick = context.world.gameTime
        val shouldForceSync = tempData.playtimeClock.isPlaying && (currentTick - tempData.lastSyncTick) >= 20

        // TODO: optimize and extract this (it should sync based on events like player join, or when the block is loaded (along with the contraption) instead of using periodic syncs

        // Sync to contraption block data if something changed OR periodic sync is needed
        if (dataChanged || shouldForceSync) {
            val block = context.contraption.blocks[context.localPos]
            val nbt = block?.nbt
            if (block != null && nbt != null) {
                nbt.putClock(tempData.playtimeClock)
                nbt.putBoolean(HAS_RECORD_KEY, hasRecord)
                nbt.putBoolean("IsPaused", tempData.isPaused)
                nbt.put("HeldRecordItem", getRecordItem(context).serializeNBT())
                context.contraption.entity.setBlockData(context.localPos, block)
                tempData.lastSyncTick = currentTick
            }
        }
    }

    private fun handleRecordUse(context: MovementContext) {
        context.world?.onServer { level ->
            val storage =
                context.contraption.storage.allItemStorages[context.localPos] as? RecordPlayerMountedStorage ?: return
            val record = getRecordItem(context)
            val result = RecordUtilities.handleRecordUse(record, RandomSource.create())

            when {
                result.shouldReplace -> {
                    result.replacementStack?.let { storage.setRecord(it) }
                }

                result.isBroken -> {
                    storage.setRecord(ItemStack.EMPTY)
                    val itemStack = (result as RecordUtilities.RecordUseResult.Broken).dropStack.copy()

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
        val tempData = getOrCreateTemporaryData(context)

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
        // Save data from temporaryData to persistent storage
        val tempData = context.temporaryData as? RecordPlayerContextData
        if (tempData != null) {
            context.data.putClock(tempData.playtimeClock)
            context.data.putBoolean(HAS_RECORD_KEY, tempData.hasRecord)
            context.data.putBoolean("IsPaused", tempData.isPaused)
            context.data.put("HeldRecordItem", getRecordItem(context).serializeNBT())
        }
    }

    private fun updateClientState(context: MovementContext) {
        val blockNbt = context.contraption.blocks[context.localPos]?.nbt ?: return

        val newHasRecord = blockNbt.getBoolean(HAS_RECORD_KEY)
        val newIsPaused = blockNbt.getBoolean("IsPaused")

        // Get or create temporaryData
        val tempData = getOrCreateTemporaryData(context)

        // Get current values BEFORE updating
        val wasPlaying = tempData.playtimeClock.isPlaying
        val currentHasRecord = tempData.hasRecord

        // Detect if clock was reset to stopped state while record is still present
        // (indicates audio ended and should restart)
        val clockWasPlaying = blockNbt.getBoolean("ClockWasPlaying")
        if (wasPlaying && !clockWasPlaying && newHasRecord) {
            tempData.audioEnded = true
        }

        // Sync the playtime clock from block NBT
        blockNbt.updateClock(tempData.playtimeClock)

        // Update hasRecord if changed
        if (currentHasRecord != newHasRecord) {
            tempData.hasRecord = newHasRecord
        }

        // Update pause state
        tempData.isPaused = newIsPaused

        // Update HeldRecordItem if present in block NBT
        if (blockNbt.contains("HeldRecordItem")) {
            val itemNbt = blockNbt.getCompound("HeldRecordItem")
            val recordStack = ItemStack.of(itemNbt)
            // Update the block entity's record display
            val be = context.contraption.getBlockEntityClientSide(context.localPos) as? RecordPlayerBlockEntity
            be?.playerBehaviour?.setRecord(recordStack)
        }
    }

    private fun getOrCreateTemporaryData(context: MovementContext): RecordPlayerContextData {
        if (context.temporaryData !is RecordPlayerContextData) {
            // Initialize temporaryData with saved data from context.data (only used on contraption load)
            val savedHasRecord = context.data.getBoolean(HAS_RECORD_KEY)
            val savedIsPaused = context.data.getBoolean("IsPaused")

            val newData =
                RecordPlayerContextData(
                    hasRecord = savedHasRecord,
                    isPaused = savedIsPaused,
                )

            // Restore the playtime clock from saved data
            context.data.updateClock(newData.playtimeClock)

            context.temporaryData = newData

            // On client side, also load the saved HeldRecordItem into the block entity for display
            context.world.onClient { _, _ ->
                if (context.data.contains("HeldRecordItem")) {
                    val itemNbt = context.data.getCompound("HeldRecordItem")
                    val recordStack = ItemStack.of(itemNbt)
                    val be = context.contraption.getBlockEntityClientSide(context.localPos) as? RecordPlayerBlockEntity
                    be?.playerBehaviour?.setRecord(recordStack)
                }

                (context.temporaryData as RecordPlayerContextData).apply {
                    this.particleJob =
                        modLaunch {
                            val player = getOrCreateAudioPlayer(getPlayerUUID(context).toString(), context)
                            val displacement = context.world.random.nextInt(4) / 24f

                            val facing = context.state.getValue(BlockStateProperties.FACING)
                            val localDirection = Vec3(facing.stepX.toDouble(), facing.stepY.toDouble(), facing.stepZ.toDouble())
                            val worldDirection = context.rotation.apply(localDirection).normalize()

                            val spawnPos = context.position.add(worldDirection.scale(0.7 + displacement))

                            player.state.collect { state ->
                                when (state) {
                                    PlayerState.LOADING -> {
                                        context.world.addParticle(
                                            ShriekParticleOption(2),
                                            false,
                                            spawnPos.x,
                                            spawnPos.y,
                                            spawnPos.z,
                                            0.0,
                                            12.5,
                                            0.0,
                                        )
                                    }

                                    PlayerState.PLAYING -> {
                                        context.world.addParticle(
                                            ParticleTypes.NOTE,
                                            spawnPos.x + displacement,
                                            spawnPos.y + displacement,
                                            spawnPos.z + displacement,
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
                                delay(10.ticks())
                            }
                        }
                }
            }
        }
        return context.temporaryData as RecordPlayerContextData
    }

    private fun buildPitchSupplier(context: MovementContext): () -> Float {
        val tempData = getOrCreateTemporaryData(context)

        val playbackMode = RecordPlayerBlockEntity.PlaybackMode.entries[context.blockEntityData.getInt("ScrollValue")]

        if (tempData.pitchSupplier == null) {
            if (playbackMode == RecordPlayerBlockEntity.PlaybackMode.PLAY_STATIC_PITCH ||
                playbackMode == RecordPlayerBlockEntity.PlaybackMode.PAUSE_STATIC_PITCH
            ) {
                return { 1.0f }
            }

            tempData.pitchSupplier =
                FloatSupplierInterpolated({
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

        return { tempData.pitchSupplier!!.getValue() }
    }

    private fun buildVolumeSupplier(context: MovementContext): () -> Float {
        val tempData = getOrCreateTemporaryData(context)
        val redstonePower = context.blockEntityData.getInt("RedstonePower")
        if (tempData.volumeSupplier == null) {
            tempData.volumeSupplier =
                FloatSupplierInterpolated({
                    if (redstonePower <= 0) return@FloatSupplierInterpolated 1f
                    redstonePower.toFloat().remapTo(1f, 15f, 0.1f, 1.0f)
                }, 500)
        }
        return { tempData.volumeSupplier!!.getValue() }
    }

    private fun buildRadiusSupplier(context: MovementContext): () -> Float {
        val tempData = getOrCreateTemporaryData(context)
        val redstonePower = context.blockEntityData.getInt("RedstonePower")
        if (tempData.radiusSupplier == null) {
            tempData.radiusSupplier =
                FloatSupplierInterpolated({
                    if (redstonePower <= 0) return@FloatSupplierInterpolated 16f
                    redstonePower.remapTo(0, 15, 4, ServerConfig.maxJukeboxSoundRange.get())
                    redstonePower.toFloat()
                }, 500)
        }
        return { tempData.radiusSupplier!!.getValue() }
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

        if (player.state.value == PlayerState.LOADING) {
            return
        }

        // Calculate desired playback state based on current local contraption state
        val tempData = getOrCreateTemporaryData(context)
        val hasRecord = tempData.hasRecord
        val currentSpeed = abs(context.animationSpeed)
        val isStalled = context.stall
        val currentTick = context.world.gameTime

        tempData.underwaterFilter.update(
            player,
            context.position,
            context.world,
        )

        val wasAudioEnded = tempData.audioEnded
        if (wasAudioEnded) {
            tempData.audioEnded = false
        }

        // Determine raw desired state (before grace period)
        val rawDesiredState =
            when {
                !hasRecord -> {
                    PlaybackState.STOPPED
                }

                context.disabled -> {
                    PlaybackState.PAUSED
                }

                currentSpeed >= SPEED_THRESHOLD || isStalled || isPauseModeWithRedstone(context) -> {
                    PlaybackState.PLAYING
                }

                else -> {
                    PlaybackState.PAUSED
                }
            }

        // Grace period logic for PAUSED state to avoid momentary interruptions
        val desiredState: PlaybackState

        if (rawDesiredState == PlaybackState.PAUSED) {
            if (tempData.pauseRequestedTick < 0) {
                tempData.pauseRequestedTick = currentTick
                return
            } else {
                // We've been wanting to pause for a while - check if grace period expired
                val pauseRequestedTick = tempData.pauseRequestedTick
                val ticksSincePauseRequested = currentTick - pauseRequestedTick

                if (ticksSincePauseRequested < PAUSE_GRACE_PERIOD_TICKS) {
                    // Still in grace period - keep playing
                    return
                } else {
                    // Grace period expired - actually pause now
                    desiredState = PlaybackState.PAUSED
                    tempData.pauseRequestedTick = -1L
                }
            }
        } else {
            // Not trying to pause, or already paused/stopped - clear grace period
            tempData.pauseRequestedTick = -1L
            desiredState = rawDesiredState
        }

        when (desiredState) {
            PlaybackState.PLAYING -> {
                when (player.state.value) {
                    PlayerState.PAUSED -> {
                        player.play()
                    }

                    PlayerState.STOPPED -> {
                        val record = getRecordItem(context)

                        player.playFromRecord(
                            record,
                            buildPitchSupplier(context),
                            buildRadiusSupplier(context),
                            buildVolumeSupplier(context),
                        )
                        player.seek(tempData.playtimeClock.currentPlaytime)
                    }

                    PlayerState.PLAYING -> {
                        player.syncWith(tempData.playtimeClock)
                    }

                    else -> {}
                }
            }

            PlaybackState.PAUSED -> {
                if (player.state.value == PlayerState.PLAYING) {
                    player.pause()
                }
            }

            PlaybackState.STOPPED -> {
                if (player.state.value != PlayerState.STOPPED) {
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
        // Get or create temporaryData
        val tempData = getOrCreateTemporaryData(context)

        val playerExists = AudioPlayerManager.containsStream(playerId)

        // Need to initialize if: not initialized yet, OR initialized but player was destroyed
        if (!tempData.playerInitialized || !playerExists) {
            // Clean up any stale player (shouldn't exist, but just in case)
            if (playerExists) {
                AudioPlayerManager.release(playerId)
            }

            // Only reset state flags, but keep playTime if it exists (e.g., when rejoining)
            // This allows the audio to resume from where it left off
            tempData.audioEnded = false
            tempData.pauseRequestedTick = -1L

            tempData.playerInitialized = true
        }

        return AudioPlayerManager.getOrCreate(
            playerId,
            provider = { streamId, stream ->
                SimpleStreamSoundInstance(
                    stream,
                    streamId,
                    SoundEvents.EMPTY,
                    posSupplier = { BlockPos.containing(context.position) },
                    radiusSupplier = buildRadiusSupplier(context),
                    volumeSupplier = buildVolumeSupplier(context),
                )
            },
            effectChainConfiguration = {
                val effects = this.getEffects()
                if (effects.none { it is PitchShiftEffect }) {
                    this.addEffectAt(
                        0,
                        PitchShiftEffect(buildPitchSupplier(context)),
                    )
                }
            },
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

    override fun renderInContraption(
        context: MovementContext,
        renderWorld: VirtualRenderWorld,
        matrices: ContraptionMatrices,
        buffer: MultiBufferSource,
    ) {
        if (VisualizationManager.supportsVisualization(context.world)) return
        RecordPlayerRenderer.renderInContraption(context, renderWorld, matrices, buffer)
    }

    override fun mustTickWhileDisabled(): Boolean = true

    override fun disableBlockEntityRendering(): Boolean = true

    private fun getPlayerUUID(context: MovementContext): UUID? {
        if (!context.blockEntityData.contains(PLAYER_UUID_KEY)) {
            "PlayerUUID not found at ${context.localPos}".err()
            return null
        }
        return context.blockEntityData.getUUID(PLAYER_UUID_KEY)
    }

    private fun getRecordItem(context: MovementContext): ItemStack {
        if (context.world.isClientSide) {
            val be =
                context.contraption.getBlockEntityClientSide(context.localPos) as? RecordPlayerBlockEntity
                    ?: return ItemStack.EMPTY
            return be.playerBehaviour.getRecord()
        }
        val handler =
            context.contraption.storage.allItemStorages[context.localPos] as? RecordPlayerMountedStorage
                ?: return ItemStack.EMPTY
        return handler.getRecord()
    }
}
