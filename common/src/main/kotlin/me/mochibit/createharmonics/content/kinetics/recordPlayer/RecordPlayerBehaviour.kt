package me.mochibit.createharmonics.content.kinetics.recordPlayer

import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld
import me.mochibit.createharmonics.audio.AudioPlayerManager
import me.mochibit.createharmonics.audio.ClientPlaybackController
import me.mochibit.createharmonics.audio.instance.StreamingSoundInstance
import me.mochibit.createharmonics.audio.player.*
import me.mochibit.createharmonics.config.ServerConfig
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBehaviour.RecordPlayerInventory.MAIN_RECORD_SLOT
import me.mochibit.createharmonics.content.records.EtherealRecordItem
import me.mochibit.createharmonics.content.records.RecordUtilities
import me.mochibit.createharmonics.foundation.extension.onClient
import me.mochibit.createharmonics.foundation.extension.onServer
import me.mochibit.createharmonics.foundation.inventory.InventorySpec
import me.mochibit.createharmonics.foundation.inventory.dropContents
import me.mochibit.createharmonics.foundation.services.contentService
import net.createmod.catnip.nbt.NBTHelper
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.core.particles.ItemParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.ShriekParticleOption
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.Vec3
import java.util.*
import kotlin.math.abs

class RecordPlayerBehaviour(
    val be: RecordPlayerBlockEntity,
) : BlockEntityBehaviour(be), PlaybackSessionOwner {
    companion object {
        @JvmStatic
        val BEHAVIOUR_TYPE = BehaviourType<RecordPlayerBehaviour>()
    }

    object RecordPlayerInventory {
        const val MAIN_RECORD_SLOT = 0
        const val RECORD_OUTPUT_SLOT = 1

        val SpecBasis = InventorySpec(
            size = 2, isItemValid = { slot, stack ->
                when {
                    stack.isEmpty -> true
                    else -> {
                        val item = stack.item as? EtherealRecordItem
                        when {
                            item == null -> false
                            slot == MAIN_RECORD_SLOT -> !item.isRecordBroken()
                            else -> true
                        }
                    }
                }
            })
    }

    private var _recordPlayerUUID: UUID? = null
    val recordPlayerUUID: UUID?
        get() = _recordPlayerUUID

    private fun ensureUUID() {
        if (_recordPlayerUUID != null) return
        if (be.level?.isClientSide != false) return
        _recordPlayerUUID = UUID.randomUUID()
        be.notifyUpdate()
    }

    private val settings get() = RecordPlayerSettings(be.playbackMode.get(), redstonePower)
    private val maxRange get() = ServerConfig.maxJukeboxSoundRange.get()

    val isStaticPitchMode get() = be.playbackMode.get().isStaticPitch
    val isPauseMode get() = be.playbackMode.get().isPause

    private val currentPitch: Float
        get() = if (isStaticPitchMode) 1f else PitchCurve.RPM.evaluate(be.speed)

    val soundRadius: Int get() = settings.radius(maxRange).toInt()

    @Volatile
    private var lastActiveVolume: Float = 1f

    val currentVolume: Float
        get() {
            val ps = obtainAudioPlayer()?.state?.value
            if (playbackState == PlaybackState.PAUSED || (ps != PlayerState.PLAYING && ps != PlayerState.LOADING)) return lastActiveVolume
            return when {
                settings.isPowered -> settings.volume
                settings.mode.isPause -> 0f
                else -> 1f
            }
        }

    @Volatile
    var playbackState: PlaybackState = PlaybackState.STOPPED
        private set

    @Volatile
    var audioPlayCount: Long = 0
        private set

    @Volatile
    var audioPlayingTitle: String? = null
        private set

    @Volatile
    var speedInterrupted = false
        private set

    @Volatile
    var redstonePower = 0
        private set

    @Volatile
    var currentlyLooping = false
        private set

    private var session: ServerPlaybackSession? = null

    /**
     * Server authoritative playtime clock that is the truth source for the players internal clock
     */
    val clock = PlaytimeClock()

    // Track previous playback mode to detect user changes
    private var previousPlaybackMode: PlaybackMode? = null

    // Track if playback ended naturally (to prevent auto-restart)
    private var playbackEndedNaturally = false

    // Flag to request restart on next tick (for redstone looping)
    private var shouldRestartOnNextTick = false

    val itemHandler = contentService.createInventoryFromSpec(RecordPlayerInventory.SpecBasis, onChange = { _: Int ->
        val hasDisc = !getStackInSlot(MAIN_RECORD_SLOT).isEmpty
        if (!hasDisc) {
            this@RecordPlayerBehaviour.onAudioTitleUpdate("")
        }
        be.level?.setBlockAndUpdate(
            be.blockPos,
            be.blockState.setValue(RecordPlayerTrait.HAS_ETHEREAL_RECORD, hasDisc),
        )
        be.notifyUpdate()
    }, onLoad = {
        (0 until this.getSlots()).forEach { RecordUtilities.migrateLegacyUrl(getStackInSlot(it)) }
        be.notifyUpdate()
    })

    private var pendingRecordUse = false
    private var ticksSinceLastClockSave = 0

    private fun obtainAudioPlayer(): AudioPlayer? {
        val uuid = _recordPlayerUUID ?: return null
        val player = AudioPlayerManager.getOrCreate(
            id = uuid.toString(),
            provider = { stream ->
                StreamingSoundInstance.simpleFactory(
                    this,
                    stream,
                    SoundEvents.EMPTY,
                )
            },
            effectChainConfiguration = { player ->

            },
        )

        player.bindSpatial(
            be,
            clock,
            BlockEntityAudioContext(be, { currentVolume }, { currentPitch }, { settings.radius(maxRange) })
        )

        return player
    }

    private val particleRandom: RandomSource = RandomSource.create()

    override fun getType(): BehaviourType<RecordPlayerBehaviour> = BEHAVIOUR_TYPE

    private fun ensureSession() {
        if (session != null) return
        val id = _recordPlayerUUID?.toString() ?: return
        session = PlaybackSessions.acquire(id, this, clock)
    }

    override fun lazyTick() {
        val level = be.level ?: return
        level.onClient { l, _ ->
            if (playbackState != PlaybackState.PLAYING) return@onClient
            val player = recordPlayerUUID?.let { AudioPlayerManager.get(it.toString()) } ?: return@onClient
            RecordPlayerParticles.emitStatic(l, player.state.value, be.blockPos)
        }
    }

    override fun tick() {
        super.tick()
        val level = be.level ?: return

        level.onServer { serverLevel ->
            clock.rate = currentPitch.toDouble()
            ensureUUID()
            ensureSession()
            saveClockPeriodically()

            itemHandler.getStackInSlot(RecordPlayerInventory.RECORD_OUTPUT_SLOT)
                .takeIf { !it.isEmpty }?.let { tryEjectOutputSlot(it, serverLevel) }

            if (pendingRecordUse) {
                pendingRecordUse = false; if (hasRecord()) handleRecordUse()
            }

            if (shouldRestartOnNextTick) {
                shouldRestartOnNextTick = false
                if (hasRecord() && abs(be.speed) > 0f) {
                    updatePlaybackState(PlaybackState.PLAYING, force = true)
                    audioPlayCount += 1
                    pendingRecordUse = true
                }
                return@onServer
            }
            applyPolicy()
        }

        level.onClient { level, virtual ->
            clock.rate = currentPitch.toDouble()
            obtainAudioPlayer()?.tick()
            if (playbackState == PlaybackState.PLAYING) {
                lastActiveVolume = currentVolume
            }
        }
    }

    private fun applyPolicy() {
        val mode = be.playbackMode.get()
        val modeChanged = previousPlaybackMode != mode
        previousPlaybackMode = mode
        val hadDisc = hasRecord()

        val d = PlaybackPolicy.decide(
            PlaybackInputs(
                hadDisc, abs(be.speed) > 0f, mode.isPause, redstonePower, playbackState,
                modeChanged, playbackEndedNaturally, speedInterrupted, currentlyLooping,
            )
        )
        currentlyLooping = d.looping
        speedInterrupted = d.speedInterrupted
        playbackEndedNaturally = d.endedNaturally
        if (d.state != playbackState) {
            if (!hadDisc) {
                audioPlayCount = 0; audioPlayingTitle = null
            }
            updatePlaybackState(d.state)
        }
        if (d.startsNewPlay) {
            audioPlayCount += 1; pendingRecordUse = true
        }
    }

    private fun saveClockPeriodically() {
        if (clock.isPlaying) {
            ticksSinceLastClockSave++
            if (ticksSinceLastClockSave >= 100) {
                ticksSinceLastClockSave = 0
                be.setChanged()
            }
        } else {
            ticksSinceLastClockSave = 0
        }
    }

    fun handleRecordUse() {
        be.level?.onServer { level ->
            val record = getRecord()
            val result = RecordUtilities.handleRecordUse(record, level)

            when {
                result.shouldReplace -> {
                    result.replacementStack?.let { setRecord(it) }
                }

                result.isBroken -> {
                    setRecord(ItemStack.EMPTY)
                    val itemStack = (result as RecordUtilities.RecordUseResult.Broken).dropStack.copy()

                    val facing = be.blockState.getValue(BlockStateProperties.FACING)
                    val dropPos = Vec3.atCenterOf(be.blockPos).add(
                        facing.stepX * 0.7,
                        facing.stepY * 0.7,
                        facing.stepZ * 0.7,
                    )

                    level.playSound(null, be.blockPos, SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, .7f, 1.7f)
                    level.playSound(null, be.blockPos, SoundEvents.SMALL_AMETHYST_BUD_BREAK, SoundSource.PLAYERS)
                    level.sendParticles(
                        ItemParticleOption(ParticleTypes.ITEM, itemStack),
                        dropPos.x,
                        dropPos.y,
                        dropPos.z,
                        16,
                        0.15,
                        0.15,
                        0.15,
                        0.08,
                    )

                    itemHandler.setStackInSlot(RecordPlayerInventory.RECORD_OUTPUT_SLOT, itemStack)
                    be.notifyUpdate()
                }
            }
        }
    }

    private fun tryEjectOutputSlot(
        stack: ItemStack,
        level: ServerLevel,
    ) {
        val facing = be.blockState.getValue(BlockStateProperties.FACING)

        val funnelResult =
            this.be.getBehaviour(DirectBeltInputBehaviour.TYPE)?.tryExportingToBeltFunnel(stack, facing.opposite, false)
        if (funnelResult != null && funnelResult.count != stack.count) {
            itemHandler.setStackInSlot(RecordPlayerInventory.RECORD_OUTPUT_SLOT, funnelResult)
            be.notifyUpdate()
            return
        }

        for (direction in Direction.entries) {
            if (direction == Direction.UP) continue

            val neighbourPos = be.blockPos.relative(direction)
            val behaviour = get(level, neighbourPos, DirectBeltInputBehaviour.TYPE) ?: continue
            if (!behaviour.canInsertFromSide(direction)) continue
            val remainder = behaviour.handleInsertion(stack, direction, false)
            if (!ItemStack.matches(remainder, stack)) {
                itemHandler.setStackInSlot(RecordPlayerInventory.RECORD_OUTPUT_SLOT, remainder)
                be.notifyUpdate()
                return
            }
        }

        val dropPos = Vec3.atCenterOf(be.blockPos).add(
            facing.stepX * 0.7,
            facing.stepY * 0.7,
            facing.stepZ * 0.7,
        )
        itemHandler.setStackInSlot(RecordPlayerInventory.RECORD_OUTPUT_SLOT, ItemStack.EMPTY)
        val itemEntity = ItemEntity(level, dropPos.x, dropPos.y, dropPos.z, stack)
        itemEntity.setDeltaMovement(facing.stepX * 0.2, 0.2, facing.stepZ * 0.2)
        level.addFreshEntity(itemEntity)
        be.notifyUpdate()
    }

    fun redstonePowerChanged(power: Int) {
        redstonePower = power
        be.notifyUpdate()
    }

    fun hasRecord(): Boolean = !itemHandler.getStackInSlot(MAIN_RECORD_SLOT).isEmpty

    fun insertRecord(discItem: ItemStack): Boolean {
        if (hasRecord()) return false
        val item = discItem.item
        if (item !is EtherealRecordItem) return false
        if (item.isRecordBroken()) return false
        itemHandler.setStackInSlot(MAIN_RECORD_SLOT, discItem.copy())
        playbackEndedNaturally = false
        return true
    }

    fun popRecord(): ItemStack? {
        if (!hasRecord()) return null
        val item = itemHandler.getStackInSlot(MAIN_RECORD_SLOT).copy()
        itemHandler.setStackInSlot(MAIN_RECORD_SLOT, ItemStack.EMPTY)
        return item
    }

    fun getRecord(): ItemStack = itemHandler.getStackInSlot(MAIN_RECORD_SLOT).copy()

    fun setRecord(discItem: ItemStack) {
        itemHandler.setStackInSlot(MAIN_RECORD_SLOT, discItem)
        if (!discItem.isEmpty) {
            playbackEndedNaturally = false
        }
    }

    fun getRecordItem(): EtherealRecordItem? {
        val recordStack = getRecord()
        if (recordStack.isEmpty || recordStack.item !is EtherealRecordItem) return null
        return recordStack.item as EtherealRecordItem
    }

    fun startPlayer() {
        when {
            !hasRecord() -> {
                updatePlaybackState(PlaybackState.STOPPED, force = true)
            }

            abs(be.speed) <= 0.0f -> {
                updatePlaybackState(PlaybackState.PAUSED)
            }

            else -> {
                updatePlaybackState(PlaybackState.PLAYING, force = true)
                audioPlayCount += 1
                pendingRecordUse = true
            }
        }
    }

    fun stopPlayer() {
        updatePlaybackState(PlaybackState.STOPPED, force = true)
    }

    fun pausePlayer() {
        updatePlaybackState(PlaybackState.PAUSED)
    }

    private fun updatePlaybackState(newState: PlaybackState, force: Boolean = false) {
        if (playbackState == newState && !force) return
        val oldState = playbackState
        playbackState = newState

        when (newState) {
            PlaybackState.PAUSED -> {
                session?.pause()
                if (oldState == PlaybackState.PLAYING) notifyStop()
            }

            PlaybackState.PLAYING if oldState != PlaybackState.PLAYING -> {
                if (oldState == PlaybackState.STOPPED) session?.reset() else session?.resume()
                notifyStart()
            }

            PlaybackState.STOPPED -> {
                session?.reset()
                if (oldState != PlaybackState.STOPPED) notifyStop()
            }

            PlaybackState.PLAYING -> {}
        }
        be.notifyUpdate()
    }

    private fun notifyStart() =
        (be.level as? ServerLevel)?.let { RecordPlayerHooks.notifyStart(it, pos, be.blockState) }

    private fun notifyStop() = (be.level as? ServerLevel)?.let { RecordPlayerHooks.notifyStop(it, pos, be.blockState) }


    override fun unload() {
        val uuidStr = recordPlayerUUID?.toString()
        if (uuidStr != null && be.isChunkUnloaded) {
            be.onServer { PlaybackSessions.release(uuidStr); notifyStop() }
            be.level?.onClient { _, _ -> AudioPlayerManager.release(uuidStr) }
        }
        contentService.invalidateBlockEntityStorage(be)
        super.unload()
    }

    override fun destroy() {
        val uuidStr = recordPlayerUUID?.toString()
        be.level?.onClient { _, _ -> uuidStr?.let(AudioPlayerManager::release) }
        be.level?.onServer { serverLevel ->
            notifyStop()
            uuidStr?.let(PlaybackSessions::release)
            itemHandler.dropContents(serverLevel, be.blockPos)
        }
        super.destroy()
    }

    override fun write(
        compound: CompoundTag,
        registries: HolderLookup.Provider,
        clientPacket: Boolean,
    ) {
        compound.put("Inventory", itemHandler.serializeNBT(registries))
        NBTHelper.writeEnum(compound, "PlaybackState", playbackState)
        _recordPlayerUUID?.let {
            compound.putUUID("RecordPlayerUUID", it)
        }

        compound.putBoolean("PlaybackEndedNaturally", playbackEndedNaturally)

        compound.putClock(clock)

        compound.putLong("AudioPlayCount", audioPlayCount)
        compound.putBoolean("SpeedInterrupted", speedInterrupted)
        compound.putInt("RedstonePower", redstonePower)
        audioPlayingTitle?.let {
            compound.putString("AudioPlayingTitle", it)
        }
    }

    override fun read(
        compound: CompoundTag,
        registries: HolderLookup.Provider,
        clientPacket: Boolean,
    ) {
        if (compound.contains("Inventory")) {
            itemHandler.deserializeNBT(registries, compound.getCompound("Inventory"))
        }

        if (compound.contains("RedstonePower")) {
            redstonePower = compound.getInt("RedstonePower")
        }

        if (compound.contains("PlaybackEndedNaturally")) {
            playbackEndedNaturally = compound.getBoolean("PlaybackEndedNaturally")
        }

        if (compound.contains("RecordPlayerUUID")) {
            val loadedUUID = compound.getUUID("RecordPlayerUUID")
            _recordPlayerUUID = loadedUUID
        }

        if (compound.contains("SpeedInterrupted")) {
            speedInterrupted = compound.getBoolean("SpeedInterrupted")
        }

        compound.updateClock(clock)

        if (compound.contains("AudioPlayingTitle")) {
            audioPlayingTitle = compound.getString("AudioPlayingTitle")
        }

        if (compound.contains(RecordPlayerNbtKeys.PLAYBACK_STATE)) {
            val newState = NBTHelper.readEnum(compound, RecordPlayerNbtKeys.PLAYBACK_STATE, PlaybackState::class.java)
            be.level?.onClient { level, _ ->
                val player = obtainAudioPlayer() ?: return@onClient
                if (newState == PlaybackState.PLAYING && getRecordItem() == null) return@onClient
                RecordPlayerHooks.notifyClient(level, pos, newState == PlaybackState.PLAYING)
                if (newState == PlaybackState.PAUSED) lastActiveVolume = currentVolume
                ClientPlaybackController.apply(player, newState, level, ::getRecord, clock.currentPlaytime)
            }
            playbackState = newState
        }

        if (compound.contains("AudioPlayCount")) {
            val newPlayCount = compound.getLong("AudioPlayCount")
            audioPlayCount = newPlayCount
        }
    }

    fun onPlaybackEnd(failure: Boolean = false) {
        if (failure) {
            shouldRestartOnNextTick = true
            return
        }
        val isFullyPowered = this.redstonePower == 15

        if (isFullyPowered) {
            playbackEndedNaturally = false
            updatePlaybackState(PlaybackState.STOPPED, force = true)
            shouldRestartOnNextTick = true
        } else {
            playbackEndedNaturally = true
            updatePlaybackState(PlaybackState.STOPPED, force = true)
        }
    }

    private fun onAudioTitleUpdate(audioTitle: String) {
        if (audioTitle == "Unknown") return
        audioPlayingTitle = audioTitle
    }

    override fun onClockChanged() = be.notifyUpdate()

    override fun onAllEnded() = onPlaybackEnd()

    override fun onRestartRequested() = onPlaybackEnd(failure = true)

    override fun onTitleChanged(title: String) = onAudioTitleUpdate(title)
}

