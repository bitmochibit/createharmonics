package me.mochibit.createharmonics.content.kinetics.recordPlayer

import com.simibubi.create.content.contraptions.ControlledContraptionEntity
import com.simibubi.create.content.contraptions.behaviour.MovementContext
import com.simibubi.create.content.contraptions.render.ActorVisual
import com.simibubi.create.content.contraptions.render.ContraptionMatrices
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.api.visualization.VisualizationManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import me.mochibit.createharmonics.audio.AudioPlayerManager
import me.mochibit.createharmonics.audio.effect.AudioEffect
import me.mochibit.createharmonics.audio.effect.EffectPreset
import me.mochibit.createharmonics.audio.effect.PitchShiftEffect
import me.mochibit.createharmonics.audio.player.AudioPlayer
import me.mochibit.createharmonics.audio.player.PlayerState
import me.mochibit.createharmonics.audio.player.PlaytimeClock
import me.mochibit.createharmonics.audio.player.putClock
import me.mochibit.createharmonics.audio.player.updateClock
import me.mochibit.createharmonics.config.ModConfigs
import me.mochibit.createharmonics.config.ServerConfig
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerMovementBehaviour.Companion.Suppliers.pitchSupplierFactory
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerMovementBehaviour.Companion.Suppliers.radiusSupplierFactory
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerMovementBehaviour.Companion.Suppliers.volumeSupplierFactory
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerMovementBehaviour.Companion.Utils.isPauseModeWithRedstone
import me.mochibit.createharmonics.content.records.RecordUtilities
import me.mochibit.createharmonics.content.records.RecordUtilities.playFromRecord
import me.mochibit.createharmonics.foundation.async.every
import me.mochibit.createharmonics.foundation.async.thenLaunch
import me.mochibit.createharmonics.foundation.behaviour.movement.SmartMovementBehaviour
import me.mochibit.createharmonics.foundation.behaviour.movement.Stainable
import me.mochibit.createharmonics.foundation.extension.onClient
import me.mochibit.createharmonics.foundation.extension.onServer
import me.mochibit.createharmonics.foundation.extension.readEnum
import me.mochibit.createharmonics.foundation.extension.remapTo
import me.mochibit.createharmonics.foundation.extension.ticks
import me.mochibit.createharmonics.foundation.extension.toNBT
import me.mochibit.createharmonics.foundation.extension.writeEnum
import me.mochibit.createharmonics.foundation.network.packet.AudioPlayerContextStopPacket
import me.mochibit.createharmonics.foundation.registry.ModPackets
import me.mochibit.createharmonics.foundation.services.contentService
import me.mochibit.createharmonics.foundation.signals.SignalBox
import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplier
import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplierInterpolated
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.ShriekParticleOption
import net.minecraft.nbt.CompoundTag
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

data class RecordPlayerContextData(
    val pitchSupplier: FloatSupplier,
    val volumeSupplier: FloatSupplier,
    val radiusSupplier: FloatSupplier,
    var particleJob: Job? = null,
    var movementPlayerInitialized: Boolean = false,
    var heldItemStack: ItemStack = ItemStack.EMPTY,
    val playtimeClock: PlaytimeClock = PlaytimeClock(),
    var playbackState: PlaybackState = PlaybackState.STOPPED,
    val underwaterFilter: EffectPreset.UnderwaterFilter = EffectPreset.UnderwaterFilter(),
    val reverberator: EffectPreset.Reverberator = EffectPreset.Reverberator(),
    var ticksSinceReverberatorUpdate: Int = 0,
    var gracefulStopJob: Job? = null,
    var ticksSinceLastClockSave: Int = 0,
    override var isDirty: Boolean = false,
) : Stainable

object GlobalRecordPlayerMovementBehaviourTracker {
    val canRestart = SignalBox<String>()
}

class RecordPlayerMovementBehaviour : SmartMovementBehaviour<RecordPlayerContextData>() {
    companion object {
        const val PLAYER_UUID_KEY = "RecordPlayerUUID"
        private const val SPEED_THRESHOLD = 0.01f

        object Suppliers {
            private fun calculateControlledContraptionPitch(context: MovementContext): Float {
                // Get min and max pitch from config
                val minPitch =
                    ModConfigs.client.minPitch
                        .get()
                        .toFloat()
                val maxPitch =
                    ModConfigs.client.maxPitch
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
                    ModConfigs.client.minPitch
                        .get()
                        .toFloat()
                val maxPitch =
                    ModConfigs.client.maxPitch
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

            fun pitchSupplierFactory(context: MovementContext): FloatSupplier {
                val playbackMode =
                    RecordPlayerBlockEntity.PlaybackMode.entries[context.blockEntityData.getInt("ScrollValue")]

                if (playbackMode == RecordPlayerBlockEntity.PlaybackMode.PLAY_STATIC_PITCH ||
                    playbackMode == RecordPlayerBlockEntity.PlaybackMode.PAUSE_STATIC_PITCH
                ) {
                    return FloatSupplier { 1.0f }
                }

                return FloatSupplierInterpolated({
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

            fun volumeSupplierFactory(context: MovementContext): FloatSupplier {
                val redstonePower = context.blockEntityData.getInt("RedstonePower")
                return FloatSupplierInterpolated({
                    if (redstonePower <= 0) return@FloatSupplierInterpolated 1f
                    redstonePower.toFloat().remapTo(1f, 15f, 0.1f, 1.0f)
                }, 500)
            }

            fun radiusSupplierFactory(context: MovementContext): FloatSupplier {
                val redstonePower = context.blockEntityData.getInt("RedstonePower")
                return FloatSupplierInterpolated({
                    if (redstonePower <= 0) return@FloatSupplierInterpolated 16f
                    redstonePower.remapTo(0, 15, 4, ServerConfig.maxJukeboxSoundRange.get()).toFloat()
                }, 500)
            }
        }

        object Utils {
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
    }

    override fun contextDataFactory(context: MovementContext): RecordPlayerContextData =
        RecordPlayerContextData(
            pitchSupplier = pitchSupplierFactory(context),
            volumeSupplier = volumeSupplierFactory(context),
            radiusSupplier = radiusSupplierFactory(context),
        ).apply {
            context.world.onClient { level, virtual ->
                this.particleJob =
                    10.ticks().every {
                        val playerUID = getPlayerUUID(context)
                        val audioPlayer = AudioPlayerManager.get(playerUID) ?: return@every
                        val displacement = Random.nextInt(4) / 24f

                        val position = context.position ?: return@every
                        val entity = context.contraption?.entity ?: return@every
                        if (this@apply.playbackState != PlaybackState.PLAYING) {
                            return@every
                        }

                        if (!entity.isAlive) {
                            cancel()
                            return@every
                        }

                        val facing = context.state.getValue(BlockStateProperties.FACING)
                        val localDirection =
                            Vec3(facing.stepX.toDouble(), facing.stepY.toDouble(), facing.stepZ.toDouble())
                        val worldDirection = context.rotation.apply(localDirection).normalize()
                        val velocity = entity.deltaMovement

                        val spawnPos =
                            position
                                .add(worldDirection.scale(0.7 + displacement))
                                .add(velocity)

                        when (audioPlayer.state.value) {
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
                                    spawnPos.x + velocity.x,
                                    spawnPos.y + velocity.y,
                                    spawnPos.z + velocity.z,
                                    worldDirection.x * 0.5,
                                    worldDirection.y * 0.5,
                                    worldDirection.z * 0.5,
                                )
                            }

                            else -> {}
                        }
                    }
            }
        }

    override fun write(
        target: CompoundTag,
        contextData: RecordPlayerContextData,
        context: MovementContext,
        syncType: SyncType,
        provider: HolderLookup.Provider,
    ) {
        target.apply {
            putClock(contextData.playtimeClock)
            put("HeldRecordItem", contextData.heldItemStack.toNBT(provider))
            if (syncType != SyncType.DISK) {
                // PlayState syncs only over network
                writeEnum("PlaybackStateMoving", contextData.playbackState)
            }
        }
    }

    override fun read(
        context: MovementContext,
        from: CompoundTag,
        target: RecordPlayerContextData,
        syncType: SyncType,
        provider: HolderLookup.Provider,
    ) {
        target.apply {
            from.updateClock(this.playtimeClock)
            heldItemStack = ItemStack.parseOptional(provider, from.getCompound("HeldRecordItem"))
            if (syncType != SyncType.DISK) {
                playbackState = from.readEnum("PlaybackStateMoving")
            }
        }
    }

    override fun mustTickWhileDisabled(): Boolean = true

    override fun disableBlockEntityRendering(): Boolean = true

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

    override fun tick(context: MovementContext) {
        context.world.onServer {
            val data = getContextData(context)
            val currentRecord = getRecordItem(context)
            if (currentRecord != data.heldItemStack) {
                data.heldItemStack = currentRecord
                data.markDirty()
            }
            data.playtimeClock.tick()

            val contraptionEntity = context.contraption?.entity
            val isDisassembled = contraptionEntity == null || !contraptionEntity.isAlive

            val newState: PlaybackState =
                if (isDisassembled || currentRecord == ItemStack.EMPTY) {
                    PlaybackState.STOPPED
                } else if (!context.disabled &&
                    (
                        abs(context.animationSpeed) >= SPEED_THRESHOLD ||
                            context.stall ||
                            isPauseModeWithRedstone(context)
                    )
                ) {
                    PlaybackState.PLAYING
                } else {
                    PlaybackState.PAUSED
                }

            if (GlobalRecordPlayerMovementBehaviourTracker.canRestart.consume(getPlayerUUID(context)) &&
                newState == PlaybackState.PLAYING
            ) {
                data.playtimeClock.stop()
                data.playbackState = PlaybackState.STOPPED
                resyncData(context, true)
                return@onServer
            }

            if (data.playbackState != newState) {
                if (newState == PlaybackState.PLAYING && data.playbackState == PlaybackState.STOPPED) {
                    handleRecordUse(context)
                }
                if (newState == PlaybackState.PLAYING) {
                    data.playbackState = newState
                }
            }

            when (newState) {
                PlaybackState.PLAYING -> {
                    data.gracefulStopJob?.cancel()
                    data.gracefulStopJob = null
                    if (!data.playtimeClock.isPlaying) {
                        data.playtimeClock.play()
                        data.markDirty()
                    }
                }

                PlaybackState.PAUSED -> {
                    // Only schedule if not already pending and not already paused
                    if (data.gracefulStopJob == null && data.playbackState != PlaybackState.PAUSED) {
                        data.gracefulStopJob =
                            1.seconds.thenLaunch {
                                data.playtimeClock.pause()
                                data.playbackState = PlaybackState.PAUSED
                                resyncData(context, false)
                                data.gracefulStopJob = null
                            }
                    }
                }

                PlaybackState.STOPPED -> {
                    if (data.gracefulStopJob == null && data.playbackState != PlaybackState.STOPPED) {
                        if (data.heldItemStack.isEmpty) {
                            // No record — stop immediately, no grace period needed
                            data.playtimeClock.stop()
                            data.playbackState = PlaybackState.STOPPED
                            data.markDirty()
                        } else {
                            data.gracefulStopJob =
                                1.seconds.thenLaunch {
                                    data.playtimeClock.stop()
                                    data.playbackState = PlaybackState.STOPPED
                                    resyncData(context, false)
                                    data.gracefulStopJob = null
                                }
                        }
                    }
                }
            }

            data.ticksSinceLastClockSave++
            if (data.ticksSinceLastClockSave >= 100) {
                data.ticksSinceLastClockSave = 0
                data.markDirty()
            }

            resyncData(context, true)
        }

        context.world.onClient { _, _ ->
            val data = getContextData(context)
            val player = getAudioPlayer(context)

            player.tick()

            data.underwaterFilter.update(player, context.position, context.world)

            data.ticksSinceReverberatorUpdate++
            if (data.ticksSinceReverberatorUpdate >= 40) {
                data.ticksSinceReverberatorUpdate = 0
                data.reverberator.update(player, context.position, context.world)
            }

            if (!data.isDirty) return
            data.clean()

            player.syncWith(data.playtimeClock)

            val newState = data.playbackState
            when (newState) {
                PlaybackState.PLAYING -> {
                    if (player.state.value == PlayerState.PLAYING) {
                        return
                    }

                    when (player.state.value) {
                        PlayerState.PAUSED -> {
                            player.play()
                        }

                        PlayerState.STOPPED -> {
                            val record = getRecordItem(context)
                            player.playFromRecord(
                                record,
                                data.pitchSupplier,
                                data.radiusSupplier,
                                data.volumeSupplier,
                                data.playtimeClock.currentPlaytime,
                                context.world,
                            )
                        }

                        else -> {}
                    }
                }

                PlaybackState.PAUSED -> {
                    if (player.state.value == PlayerState.PAUSED) {
                        return
                    }

                    player.pause()
                }

                PlaybackState.STOPPED -> {
                    if (player.state.value == PlayerState.STOPPED) {
                        return
                    }
                    player.stop()
                }
            }
        }
    }

    override fun stopMoving(context: MovementContext) {
        context.world.onServer {
            .1.seconds.thenLaunch {
                val contraptionEntity = context.contraption?.entity ?: return@thenLaunch
                if (!contraptionEntity.isAlive) {
                    stopClientAudio(context)
                }
            }
        }
    }

    fun getAudioPlayer(context: MovementContext): AudioPlayer {
        val playerId = getPlayerUUID(context)
        val data = getContextData(context)

        if (!data.movementPlayerInitialized) {
            if (AudioPlayerManager.exists(playerId)) {
                AudioPlayerManager.release(playerId)
            }
            data.movementPlayerInitialized = true
        }

        return AudioPlayerManager.getOrCreate(
            playerId,
            provider = { streamId, stream ->
                contentService.streamingSoundInstanceFactory(
                    stream,
                    streamId,
                    SoundEvents.EMPTY,
                    posSupplier = { BlockPos.containing(context.position) },
                    radiusSupplier = data.radiusSupplier,
                    volumeSupplier = data.volumeSupplier,
                )
            },
            effectChainConfiguration = {
                val effects = this.getEffects()
                if (effects.none { it is PitchShiftEffect }) {
                    this.addEffectAt(
                        0,
                        PitchShiftEffect(data.pitchSupplier, scope = AudioEffect.Scope.MACHINE_CONTROLLED_PITCH),
                    )
                }
            },
        )
    }

    fun getPlayerUUID(context: MovementContext): String {
        require(
            context.blockEntityData.contains(PLAYER_UUID_KEY),
        ) {
            "Player UUID not found, something is very wrong here! Try replacing the record player block on the contraption ${context.localPos}"
        }
        return context.blockEntityData.getUUID(PLAYER_UUID_KEY).toString()
    }

    private fun handleRecordUse(context: MovementContext) {
        context.world?.onServer { level ->
            val storage =
                context.contraption.storage.allItemStorages[context.localPos] as? RecordPlayerMountedStorage ?: return
            val record = getRecordItem(context)
            val result = RecordUtilities.handleRecordUse(record, level)

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

    private fun getRecordItem(context: MovementContext): ItemStack {
        if (context.world.isClientSide) {
            val contextData = getContextData(context)
            return contextData.heldItemStack
        }
        val handler =
            context.contraption.storage.allItemStorages[context.localPos] as? RecordPlayerMountedStorage
                ?: return ItemStack.EMPTY
        return handler.getRecord()
    }

    private fun stopClientAudio(context: MovementContext) {
        val playerUUID = getPlayerUUID(context)
        ModPackets.broadcast(AudioPlayerContextStopPacket(playerUUID))
    }
}
