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
import me.mochibit.createharmonics.ServerConfig
import me.mochibit.createharmonics.audio.AudioPlayer
import me.mochibit.createharmonics.audio.AudioPlayerRegistry
import me.mochibit.createharmonics.audio.effect.LowPassFilterEffect
import me.mochibit.createharmonics.audio.instance.SimpleStreamSoundInstance
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBehaviour.PlaybackState
import me.mochibit.createharmonics.content.records.EtherealRecordItem
import me.mochibit.createharmonics.content.records.EtherealRecordItem.Companion.playFromRecord
import me.mochibit.createharmonics.coroutine.MinecraftClientDispatcher
import me.mochibit.createharmonics.coroutine.launchDelayed
import me.mochibit.createharmonics.extension.lerpTo
import me.mochibit.createharmonics.extension.onClient
import me.mochibit.createharmonics.extension.onServer
import me.mochibit.createharmonics.extension.remapTo
import me.mochibit.createharmonics.foundation.math.FloatSupplierInterpolated
import me.mochibit.createharmonics.network.packet.AudioPlayerContextStopPacket
import me.mochibit.createharmonics.network.packet.setBlockData
import me.mochibit.createharmonics.registry.ModConfigurations
import me.mochibit.createharmonics.registry.ModPackets
import net.createmod.catnip.math.VecHelper
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.ShriekParticleOption
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.FluidTags
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.Vec3
import net.minecraftforge.network.PacketDistributor
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.Ship
import thedarkcolour.kotlinforforge.forge.vectorutil.v3d.toVec3
import thedarkcolour.kotlinforforge.forge.vectorutil.v3d.toVector3d
import java.util.UUID
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

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
        var playTime: Long = 0L,
        var hasRecord: Boolean = false,
        var isPaused: Boolean = false, // Track if currently paused
        var pauseStartTime: Long = 0L, // Track when pause started (milliseconds)
        var totalPausedTime: Long = 0L, // Total accumulated paused time (milliseconds)
        var pausedAtOffset: Double = 0.0, // Track playback position (in seconds) when paused [DEPRECATED - kept for compatibility]
        // Runtime flags (not persisted to disk)
        var audioEnded: Boolean = false,
        var pauseRequestedTick: Long = -1L,
        var playerInitialized: Boolean = false,
        var skipNextUpdate: Boolean = false,
        var lastSyncTick: Long = 0L, // Track when we last synced to force periodic updates
        val lazyTickRate: Int = 1,
        var tickCounter: Int = lazyTickRate,
        // Interpolated suppliers for underwater filter effect
        // Start with "no filter" values (very high cutoff, flat resonance)
        var targetCutoffFrequency: Float = 20000f,
        var targetResonance: Float = 0.707f,
        var cutoffFrequencyInterpolated: FloatSupplierInterpolated? = null,
        var resonanceInterpolated: FloatSupplierInterpolated? = null,
    )

    companion object {
        private const val PLAY_TIME_KEY = "PlayTime"
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

            // Reset play time to 0
            tempData?.playTime = 0L

            // Set flag to prevent updateServerData from overwriting this on the next tick
            tempData?.skipNextUpdate = true

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
                    isPauseModeWithRedstone(
                        context,
                    )
            )
        val shouldBePaused = hasRecord && !shouldBePlaying

        // Track old values to detect changes
        val oldPlayTime = tempData.playTime
        val oldHasRecord = tempData.hasRecord
        val oldIsPaused = tempData.isPaused

        var dataChanged = false

        // Update playTime: set to current time when starting playback, otherwise keep existing value
        val newPlayTime =
            if (shouldBePlaying) {
                // Only set playTime when starting playback (was 0), otherwise keep the existing start time
                if (oldPlayTime == 0L) {
                    System.currentTimeMillis()
                } else {
                    oldPlayTime // Keep the original start time
                }
            } else if (!hasRecord) {
                // Only reset playTime when record is removed
                0L
            } else {
                oldPlayTime // Keep existing playTime during pauses
            }

        // Update pause state
        if (oldIsPaused != shouldBePaused) {
            dataChanged = true

            if (shouldBePaused) {
                // Entering pause state
                tempData.isPaused = true
                if (newPlayTime > 0L) {
                    // Record when the pause started
                    tempData.pauseStartTime = System.currentTimeMillis()
                }
            } else {
                // Exiting pause state (resuming)
                tempData.isPaused = false
                if (tempData.pauseStartTime > 0L) {
                    // Accumulate the paused duration
                    tempData.totalPausedTime += (System.currentTimeMillis() - tempData.pauseStartTime)
                    tempData.pauseStartTime = 0L
                }
            }
        }

        // Check if data actually changed
        if (oldPlayTime != newPlayTime) {
            // If we're starting playback (oldPlayTime was 0 and newPlayTime is now set)
            if (hasRecord && newPlayTime > 0L && oldPlayTime == 0L) {
                // Starting fresh playback - reset all pause tracking
                tempData.pauseStartTime = 0L
                tempData.totalPausedTime = 0L
                handleRecordUse(context)
            }
            tempData.playTime = newPlayTime
            dataChanged = true
        }

        if (oldHasRecord != hasRecord) {
            tempData.hasRecord = hasRecord
            // Reset pause tracking when record is removed
            if (!hasRecord) {
                tempData.pauseStartTime = 0L
                tempData.totalPausedTime = 0L
            }
            dataChanged = true
        }

        // Force periodic sync even if nothing changed locally (for rejoining clients)
        // Sync at least every 20 ticks (1 second) when playing
        val currentTick = context.world.gameTime
        val shouldForceSync = shouldBePlaying && (currentTick - tempData.lastSyncTick) >= 20

        // Sync to contraption block data if something changed OR periodic sync is needed
        if (dataChanged || shouldForceSync) {
            val block = context.contraption.blocks[context.localPos]
            val nbt = block?.nbt
            if (block != null && nbt != null) {
                nbt.putLong(PLAY_TIME_KEY, tempData.playTime)
                nbt.putBoolean(HAS_RECORD_KEY, hasRecord)
                nbt.putBoolean("IsPaused", tempData.isPaused)
                nbt.putLong("PauseStartTime", tempData.pauseStartTime)
                nbt.putLong("TotalPausedTime", tempData.totalPausedTime)
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
        val tempData = getOrCreateTemporaryData(context)
        if (tempData.tickCounter-- <= 0) {
            tempData.tickCounter = tempData.lazyTickRate
            tickLazy(context)
        }

        context.world.onServer {
            registerContextIfNotPresent(getPlayerUUID(context).toString(), context)
            updateServerData(context)
        }

        context.world.onClient { _, _ ->
            updateClientState(context)
            handleClientPlayback(context)
        }
    }

    fun tickLazy(context: MovementContext) {
        context.world.onClient { level, virtual ->
            updateUnderwaterFilter(context)
        }
    }

    private fun countLiquidCoveredFaces(context: MovementContext): Pair<Int, Boolean> {
        val level = context.world ?: return 0 to false
        val pos = BlockPos.containing(context.position)

        var liquidCount = 0
        var viscousCount = 0
        var waterCount = 0

        for (direction in Direction.entries) {
            val relativePos = pos.relative(direction)

            val fluidState = level.getFluidState(relativePos)

            if (!fluidState.isEmpty) {
                liquidCount++

                when {
                    fluidState.fluidType.viscosity > 1000 -> viscousCount++
                    fluidState.`is`(FluidTags.WATER) -> waterCount++
                }
            }
        }

        val isThick = viscousCount >= waterCount && viscousCount > 0

        return liquidCount to isThick
    }

    /**
     * Updates or adds a low-pass filter with the specified parameters.
     * If the filter exists, updates its parameters. Otherwise, adds a new filter.
     */
    private fun applyLowPassFilter(
        context: MovementContext,
        cutoffFrequency: Float,
        resonance: Float,
    ) {
        val playerUUID = getPlayerUUID(context) ?: return
        val audioPlayer = getOrCreateAudioPlayer(playerUUID.toString(), context)
        val tempData = getOrCreateTemporaryData(context)

        // Initialize interpolated suppliers if needed
        if (tempData.cutoffFrequencyInterpolated == null) {
            tempData.cutoffFrequencyInterpolated = FloatSupplierInterpolated({ tempData.targetCutoffFrequency }, 1000)
        }
        if (tempData.resonanceInterpolated == null) {
            tempData.resonanceInterpolated = FloatSupplierInterpolated({ tempData.targetResonance }, 1000)
        }

        // Update target values for interpolation
        tempData.targetCutoffFrequency = cutoffFrequency
        tempData.targetResonance = resonance

        val effectChain = audioPlayer.getCurrentEffectChain() ?: return
        val effects = effectChain.getEffects()
        val existingFilter = effects.firstOrNull { it is LowPassFilterEffect } as? LowPassFilterEffect

        if (existingFilter == null) {
            effectChain.addEffect(
                LowPassFilterEffect(
                    tempData.cutoffFrequencyInterpolated!!,
                    tempData.resonanceInterpolated!!,
                ),
            )
        }
    }

    /**
     * Removes the low-pass filter from the effect chain if it exists.
     */
    private fun removeLowPassFilter(context: MovementContext) {
        val playerUUID = getPlayerUUID(context) ?: return
        val audioPlayer = getOrCreateAudioPlayer(playerUUID.toString(), context)
        val tempData = getOrCreateTemporaryData(context)

        // Initialize interpolated suppliers if needed
        if (tempData.cutoffFrequencyInterpolated == null) {
            tempData.cutoffFrequencyInterpolated = FloatSupplierInterpolated({ tempData.targetCutoffFrequency }, 500)
        }
        if (tempData.resonanceInterpolated == null) {
            tempData.resonanceInterpolated = FloatSupplierInterpolated({ tempData.targetResonance }, 500)
        }

        // Set target values back to default (no filter effect)
        tempData.targetCutoffFrequency = 20000f // Very high cutoff = no filtering
        tempData.targetResonance = 0.707f // Flat response

        val effectChain = audioPlayer.getCurrentEffectChain() ?: return
        val effects = effectChain.getEffects()
        val lowPassIndex = effects.indexOfFirst { it is LowPassFilterEffect }
        effectChain.removeEffectAt(lowPassIndex, true)
    }

    private fun updateUnderwaterFilter(context: MovementContext) {
        val (liquidCoveredFaces, isThick) = countLiquidCoveredFaces(context)
        if (liquidCoveredFaces > 0) {
            val maxEffectiveFaces = 4f
            val minimumCutoff = if (isThick) 200f else 300f
            val maximumResonance = if (isThick) 2.5f else 2f

            val faceCount = liquidCoveredFaces.coerceAtMost(maxEffectiveFaces.toInt())
            val cutoffFrequency = 1800f.lerpTo(minimumCutoff, 1 / maxEffectiveFaces * faceCount)
            val resonance = 1f.lerpTo(maximumResonance, 1 / maxEffectiveFaces * faceCount)

            applyLowPassFilter(context, cutoffFrequency, resonance)
        } else {
            removeLowPassFilter(context)
        }
    }

    override fun writeExtraData(context: MovementContext) {
        // Save data from temporaryData to persistent storage
        val tempData = context.temporaryData as? RecordPlayerContextData
        if (tempData != null) {
            context.data.putLong(PLAY_TIME_KEY, tempData.playTime)
            context.data.putBoolean(HAS_RECORD_KEY, tempData.hasRecord)
            context.data.putBoolean("IsPaused", tempData.isPaused)
            context.data.putLong("PauseStartTime", tempData.pauseStartTime)
            context.data.putLong("TotalPausedTime", tempData.totalPausedTime)
            context.data.put("HeldRecordItem", getRecordItem(context).serializeNBT())
        }
    }

    private fun updateClientState(context: MovementContext) {
        val blockNbt = context.contraption.blocks[context.localPos]?.nbt ?: return

        val newPlayTime = blockNbt.getLong(PLAY_TIME_KEY)
        val newHasRecord = blockNbt.getBoolean(HAS_RECORD_KEY)
        val newIsPaused = blockNbt.getBoolean("IsPaused")
        val newPauseStartTime = blockNbt.getLong("PauseStartTime")
        val newTotalPausedTime = blockNbt.getLong("TotalPausedTime")

        // Get or create temporaryData
        val tempData = getOrCreateTemporaryData(context)

        // Get current values BEFORE updating
        val currentPlayTime = tempData.playTime
        val currentHasRecord = tempData.hasRecord

        // Detect if play time was reset to 0 (indicates audio ended and should restart)
        // This happens when the server receives the stream end packet
        if (currentPlayTime > 0 && newPlayTime == 0L && newHasRecord) {
            // Mark that audio ended so we can trigger a clean restart
            tempData.audioEnded = true
        }

        // Update play time in temporaryData if changed
        if (currentPlayTime != newPlayTime) {
            tempData.playTime = newPlayTime
        }

        // Update hasRecord if changed
        if (currentHasRecord != newHasRecord) {
            tempData.hasRecord = newHasRecord
        }

        // Update pause state
        tempData.isPaused = newIsPaused
        tempData.pauseStartTime = newPauseStartTime
        tempData.totalPausedTime = newTotalPausedTime

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
            val savedPlayTime = context.data.getLong(PLAY_TIME_KEY)
            val savedHasRecord = context.data.getBoolean(HAS_RECORD_KEY)
            val savedIsPaused = context.data.getBoolean("IsPaused")
            val savedPauseStartTime = context.data.getLong("PauseStartTime")
            val savedTotalPausedTime = context.data.getLong("TotalPausedTime")
            context.temporaryData =
                RecordPlayerContextData(
                    pitchSupplier = null,
                    playTime = savedPlayTime,
                    hasRecord = savedHasRecord,
                    isPaused = savedIsPaused,
                    pauseStartTime = savedPauseStartTime,
                    totalPausedTime = savedTotalPausedTime,
                )

            // On client side, also load the saved HeldRecordItem into the block entity for display
            context.world.onClient { _, _ ->
                if (context.data.contains("HeldRecordItem")) {
                    val itemNbt = context.data.getCompound("HeldRecordItem")
                    val recordStack = ItemStack.of(itemNbt)
                    val be = context.contraption.getBlockEntityClientSide(context.localPos) as? RecordPlayerBlockEntity
                    be?.playerBehaviour?.setRecord(recordStack)
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

    private fun buildRadiusSupplier(context: MovementContext): () -> Int {
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
        return { tempData.radiusSupplier!!.getValue().toInt() }
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
        val tempData = getOrCreateTemporaryData(context)
        val hasRecord = tempData.hasRecord
        val playTime = tempData.playTime
        val currentSpeed = abs(context.animationSpeed)
        val isStalled = context.stall
        val currentTick = context.world.gameTime

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
                when (player.state) {
                    AudioPlayer.PlayState.PAUSED -> {
                        player.resume()
                    }

                    AudioPlayer.PlayState.STOPPED -> {
                        val record = getRecordItem(context)
                        val offsetSeconds =
                            if (playTime > 0) {
                                val elapsedTime = System.currentTimeMillis() - playTime
                                val adjustedTime = elapsedTime - tempData.totalPausedTime
                                adjustedTime / 1000.0
                            } else {
                                0.0
                            }

                        player.playFromRecord(
                            record,
                            offsetSeconds,
                            buildPitchSupplier(context),
                            buildRadiusSupplier(context),
                            buildVolumeSupplier(context),
                        )
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
        // Get or create temporaryData
        val tempData = getOrCreateTemporaryData(context)

        val playerExists = AudioPlayerRegistry.containsStream(playerId)

        // Need to initialize if: not initialized yet, OR initialized but player was destroyed
        if (!tempData.playerInitialized || !playerExists) {
            // Clean up any stale player (shouldn't exist, but just in case)
            if (playerExists) {
                val existingPlayer = AudioPlayerRegistry.getPlayer(playerId)
                existingPlayer?.stopSoundImmediately()
                AudioPlayerRegistry.destroyPlayer(playerId)
            }

            // Only reset state flags, but keep playTime if it exists (e.g., when rejoining)
            // This allows the audio to resume from where it left off
            tempData.audioEnded = false
            tempData.pauseRequestedTick = -1L

            tempData.playerInitialized = true
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
                        radiusSupplier = buildRadiusSupplier(context),
                        volumeSupplier = buildVolumeSupplier(context),
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

    override fun mustTickWhileDisabled(): Boolean = true

    override fun disableBlockEntityRendering(): Boolean = true

    private fun getPlayerUUID(context: MovementContext): UUID? {
        if (!context.blockEntityData.contains(PLAYER_UUID_KEY)) {
            Logger.err("PlayerUUID not found at ${context.localPos}")
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
