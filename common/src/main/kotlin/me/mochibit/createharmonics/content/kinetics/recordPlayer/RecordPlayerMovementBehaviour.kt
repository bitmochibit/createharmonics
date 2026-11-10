package me.mochibit.createharmonics.content.kinetics.recordPlayer

import com.simibubi.create.api.contraption.storage.item.WrapperMountedItemStorage
import com.simibubi.create.content.contraptions.ControlledContraptionEntity
import com.simibubi.create.content.contraptions.behaviour.MovementContext
import com.simibubi.create.content.contraptions.render.ActorVisual
import com.simibubi.create.content.contraptions.render.ContraptionMatrices
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld
import com.simibubi.create.infrastructure.config.AllConfigs
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.api.visualization.VisualizationManager
import me.mochibit.createharmonics.audio.AudioPlayerManager
import me.mochibit.createharmonics.audio.ClientPlaybackController
import me.mochibit.createharmonics.audio.player.*
import me.mochibit.createharmonics.config.ModConfigs
import me.mochibit.createharmonics.config.ServerConfig
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBehaviour.RecordPlayerInventory.MAIN_RECORD_SLOT
import me.mochibit.createharmonics.content.records.RecordUtilities
import me.mochibit.createharmonics.foundation.behaviour.movement.SmartMovementBehaviour
import me.mochibit.createharmonics.foundation.behaviour.movement.Stainable
import me.mochibit.createharmonics.foundation.behaviour.movement.StopAwareData
import me.mochibit.createharmonics.foundation.extension.*
import me.mochibit.createharmonics.foundation.network.packet.AudioPlayerContextStopPacket
import me.mochibit.createharmonics.foundation.registry.ModPackets
import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplier
import me.mochibit.createharmonics.foundation.warn
import net.createmod.catnip.nbt.NBTHelper
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.particles.ItemParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.items.ItemHandlerHelper
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper

class RecordPlayerContextData(
    var settings: RecordPlayerSettings,
    val clock: PlaytimeClock,
    initialState: PlaybackState,
    animationSpeed: () -> Float,
    pitchCurve: () -> PitchCurve,
) : Stainable, StopAwareData, PlaybackSessionOwner {
    override var isDirty = false
    override var stopMovingCalled = false

    var playbackState = initialState
        set(v) {
            if (field != v) {
                field = v; markDirty()
            }
        }

    var heldItemStack: ItemStack = ItemStack.EMPTY
    val gate = PlaybackGate()
    var session: ServerPlaybackSession? = null
    var restartRequested = false
    var contraptionAlive = true
    var ticksSinceClockSave = 0
    var particleCooldown = 0

    val pitchSupplier = FloatSupplier {
        if (settings.mode.isStaticPitch) 1f
        else pitchCurve().evaluate(
            animationSpeed(),
            ModConfigs.server.minPitch.get().toFloat(),
            ModConfigs.server.maxPitch.get().toFloat(),
        )
    }
    val volumeSupplier = FloatSupplier { settings.volume }
    val radiusSupplier = FloatSupplier { settings.radius(ServerConfig.maxJukeboxSoundRange.get()) }

    override val isAlive get() = contraptionAlive
    override fun onClockChanged() = markDirty()
    override fun onAllEnded() {
        restartRequested = true
    }

    override fun onRestartRequested() {
        restartRequested = true
    }
}

class RecordPlayerMovementBehaviour : SmartMovementBehaviour<RecordPlayerContextData>() {

    override fun contextDataFactory(context: MovementContext) = RecordPlayerContextData(
        settings = RecordPlayerSettings.from(context.blockEntityData),
        clock = PlaytimeClock(context.blockEntityData),
        initialState = if (context.blockEntityData.contains(RecordPlayerNbtKeys.PLAYBACK_STATE))
            NBTHelper.readEnum(context.blockEntityData, RecordPlayerNbtKeys.PLAYBACK_STATE, PlaybackState::class.java)
        else PlaybackState.STOPPED,
        animationSpeed = { context.animationSpeed },
        pitchCurve = {
            if (context.contraption?.entity is ControlledContraptionEntity) PitchCurve.CONTROLLED_CONTRAPTION
            else PitchCurve.VEHICLE_CONTRAPTION
        },
    ).also { data ->
        context.world.onServer { _ ->
            playerUuid(context)?.let { data.session = PlaybackSessions.acquire(it, data, data.clock) }
        }
    }

    override fun onStopTracking(blockEntityData: CompoundTag) {
        if (!blockEntityData.contains(RecordPlayerNbtKeys.UUID)) return
        ModPackets.broadcast(AudioPlayerContextStopPacket(blockEntityData.getUUID(RecordPlayerNbtKeys.UUID).toString()))
    }

    override fun write(
        target: CompoundTag, contextData: RecordPlayerContextData, context: MovementContext,
        syncType: SyncType, provider: HolderLookup.Provider
    ) {
        target.apply {
            putClock(contextData.clock)
            put(RecordPlayerNbtKeys.HELD_RECORD, contextData.heldItemStack.toNBT(provider))
            if (syncType != SyncType.DISK) writeEnum(
                RecordPlayerNbtKeys.PLAYBACK_STATE_MOVING,
                contextData.playbackState
            )
        }
    }

    override fun read(
        context: MovementContext, from: CompoundTag, target: RecordPlayerContextData,
        syncType: SyncType, provider: HolderLookup.Provider
    ) {
        target.apply {
            if (from.contains(RecordPlayerNbtKeys.CLOCK_OFFSET)) {
                when {
                    context.world.isClientSide -> from.updateClock(clock)
                    syncType == SyncType.DISK -> clock.updateValues(
                        from.getDouble(RecordPlayerNbtKeys.CLOCK_OFFSET),
                        false
                    )
                }
            }
            if (from.contains(RecordPlayerNbtKeys.HELD_RECORD))
                heldItemStack = ItemStack.parseOptional(provider, from.getCompound(RecordPlayerNbtKeys.HELD_RECORD))
            if (from.contains(RecordPlayerNbtKeys.REDSTONE_POWER) || from.contains(RecordPlayerNbtKeys.SCROLL_VALUE))
                settings = RecordPlayerSettings.from(from)
            if (syncType != SyncType.DISK && from.contains(RecordPlayerNbtKeys.PLAYBACK_STATE_MOVING))
                playbackState = from.readEnum(RecordPlayerNbtKeys.PLAYBACK_STATE_MOVING)
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
        context.world.onServer { tickServer(context) }
        context.world.onClient { _, _ -> tickClient(context) }
    }

    private fun tickServer(context: MovementContext) {
        val data = getContextData(context)
        val session = data.session ?: return

        val record = getRecordItem(context)
        if (!ItemStack.matches(record, data.heldItemStack)) {
            data.heldItemStack = record.copy()
            data.markDirty()
        }

        val entity = context.contraption?.entity
        data.contraptionAlive = entity != null && entity.isAlive
        val target = MovementPlaybackPolicy.target(
            disassembled = !data.contraptionAlive,
            hasRecord = !record.isEmpty,
            disabled = context.disabled,
            animationSpeed = context.animationSpeed,
            stalled = context.stall,
            pauseModeWithRedstone = data.settings.mode.isPause && data.settings.isPowered,
        )

        if (data.restartRequested) {
            data.restartRequested = false
            if (target == PlaybackState.PLAYING) {
                data.playbackState = PlaybackState.STOPPED
                resyncData(context, true)
                return
            }
        }

        data.gate.next(data.playbackState, target, immediate = record.isEmpty)?.let { to ->
            when (to) {
                PlaybackState.PLAYING -> if (data.playbackState == PlaybackState.STOPPED) handleRecordUse(context)
                PlaybackState.PAUSED -> session.pause()
                PlaybackState.STOPPED -> session.reset()
            }
            data.playbackState = to
        }
        if (data.playbackState == PlaybackState.PLAYING) session.resume()

        if (++data.ticksSinceClockSave >= 100) {
            data.ticksSinceClockSave = 0; data.markDirty()
        }
        resyncData(context, true)
    }

    private fun tickClient(context: MovementContext) {
        val data = getContextData(context)
        val id = playerUuid(context) ?: return
        val player = if (data.playbackState == PlaybackState.STOPPED) AudioPlayerManager.get(id)
        else AudioPlayerManager.getOrCreate(id).also {
            it.bindSpatial(
                context, data.clock,
                ContraptionAudioContext(context, data.volumeSupplier, data.pitchSupplier, data.radiusSupplier)
            )
        }
        player?.tick()

        if (player != null && data.playbackState == PlaybackState.PLAYING && --data.particleCooldown <= 0) {
            data.particleCooldown = 10
            val pos = context.position
            val entity = context.contraption?.entity
            if (pos != null && entity != null)
                RecordPlayerParticles.emitMoving(
                    context.world,
                    player.state.value,
                    pos,
                    worldFacing(context),
                    entity.deltaMovement
                )
        }

        if (!data.isDirty) return
        data.clean()
        player?.let {
            ClientPlaybackController.apply(
                it,
                data.playbackState,
                context.world,
                { data.heldItemStack },
                data.clock.currentPlaytime
            )
        }
    }

    override fun stopMoving(context: MovementContext) {
        context.world.onServer { getContextData(context).stopMovingCalled = true }
    }

    private fun handleRecordUse(context: MovementContext) {
        context.world?.onServer { level ->
            val storage =
                context.contraption.storage.allItemStorages[context.localPos] as? WrapperMountedItemStorage<*> ?: return


            val record = getRecordItem(context)
            val result = RecordUtilities.handleRecordUse(record, level)

            when {
                result.shouldReplace -> {
                    result.replacementStack?.let {
                        storage.setStackInSlot(
                            MAIN_RECORD_SLOT, it
                        )
                    }
                }

                result.isBroken -> {
                    storage.setStackInSlot(
                        MAIN_RECORD_SLOT, ItemStack.EMPTY
                    )
                    val itemStack = (result as RecordUtilities.RecordUseResult.Broken).dropStack.copy()

                    val remainder: ItemStack = if (AllConfigs.server().kinetics.moveItemsToStorage.get()) {
                        val otherStorages =
                            context.contraption.storage.allItemStorages.filterKeys { it != context.localPos }.values.toTypedArray()

                        if (otherStorages.isNotEmpty()) {
                            ItemHandlerHelper.insertItem(
                                CombinedInvWrapper(*otherStorages),
                                itemStack,
                                false,
                            )
                        } else {
                            itemStack
                        }
                    } else {
                        itemStack
                    }

                    val vec = context.position ?: return
                    val pos = BlockPos.containing(vec)

                    level.playSound(null, pos, SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, .7f, 1.7f)
                    level.playSound(null, pos, SoundEvents.SMALL_AMETHYST_BUD_BREAK, SoundSource.PLAYERS)
                    level.sendParticles(
                        ItemParticleOption(ParticleTypes.ITEM, itemStack),
                        pos.x.toDouble(),
                        pos.y.toDouble(),
                        pos.z.toDouble(),
                        16,
                        0.15,
                        0.15,
                        0.15,
                        0.08,
                    )

                    if (remainder.isEmpty) return

                    val facing = context.state.getValue(BlockStateProperties.FACING)
                    val localDirection = Vec3(facing.stepX.toDouble(), facing.stepY.toDouble(), facing.stepZ.toDouble())
                    val worldDirection = context.rotation.apply(localDirection).normalize()
                    val dropPos = Vec3.atCenterOf(pos).add(worldDirection.scale(0.7))

                    val itemEntity = ItemEntity(level, dropPos.x, dropPos.y, dropPos.z, remainder)
                    itemEntity.deltaMovement = worldDirection.scale(0.3)
                    level.addFreshEntity(itemEntity)
                }
            }
        }
    }


    private fun worldFacing(context: MovementContext): Vec3 {
        val f = context.state.getValue(BlockStateProperties.FACING)
        return context.rotation.apply(Vec3(f.stepX.toDouble(), f.stepY.toDouble(), f.stepZ.toDouble())).normalize()
    }

    private fun getRecordItem(context: MovementContext): ItemStack =
        if (context.world.isClientSide) getContextData(context).heldItemStack
        else (context.contraption.storage.allItemStorages[context.localPos] as? WrapperMountedItemStorage<*>)
            ?.getStackInSlot(MAIN_RECORD_SLOT) ?: ItemStack.EMPTY

    private fun playerUuid(context: MovementContext): String? =
        context.blockEntityData.takeIf { it.contains(RecordPlayerNbtKeys.UUID) }?.getUUID(RecordPlayerNbtKeys.UUID)
            ?.toString()
            ?: run { "Missing UUID from a record player in a contraption!".warn(); null }
}
