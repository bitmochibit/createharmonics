package me.mochibit.createharmonics.content.kinetics.recordPlayer

import com.simibubi.create.AllBlocks
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld
import me.mochibit.createharmonics.audio.AudioPlayerManager
import me.mochibit.createharmonics.audio.effect.AudioEffect
import me.mochibit.createharmonics.audio.effect.EffectPreset
import me.mochibit.createharmonics.audio.effect.PitchShiftEffect
import me.mochibit.createharmonics.audio.instance.StreamingSoundInstance
import me.mochibit.createharmonics.audio.player.AudioPlayer
import me.mochibit.createharmonics.audio.player.BlockEntityAudioContext
import me.mochibit.createharmonics.audio.player.PlayerState
import me.mochibit.createharmonics.audio.player.PlaytimeClock
import me.mochibit.createharmonics.audio.player.putClock
import me.mochibit.createharmonics.audio.player.updateClock
import me.mochibit.createharmonics.config.ModConfigs
import me.mochibit.createharmonics.config.ServerConfig
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerItemHandler.Companion.MAIN_RECORD_SLOT
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerItemHandler.Companion.RECORD_OUTPUT_SLOT
import me.mochibit.createharmonics.content.records.EtherealRecordItem
import me.mochibit.createharmonics.content.records.RecordUtilities
import me.mochibit.createharmonics.content.records.RecordUtilities.handleRecordUse
import me.mochibit.createharmonics.content.records.RecordUtilities.playFromRecord
import me.mochibit.createharmonics.foundation.async.every
import me.mochibit.createharmonics.foundation.extension.onClient
import me.mochibit.createharmonics.foundation.extension.onServer
import me.mochibit.createharmonics.foundation.extension.remapTo
import me.mochibit.createharmonics.foundation.extension.ticks
import me.mochibit.createharmonics.foundation.network.packet.AudioPlayerContextStopPacket
import me.mochibit.createharmonics.foundation.registry.ModPackets
import net.createmod.catnip.nbt.NBTHelper
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.core.Direction
import net.minecraft.core.particles.ItemParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.ShriekParticleOption
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.Containers
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.util.LazyOptional
import org.joml.Vector3d
import java.util.UUID
import kotlin.math.abs

class RecordPlayerBehaviour(
    val be: RecordPlayerBlockEntity,
) : BlockEntityBehaviour(be) {
    companion object {
        @JvmStatic
        val BEHAVIOUR_TYPE = BehaviourType<RecordPlayerBehaviour>()

        // Static tracking for active record players by their audio player UUID
        // This prevents UUID conflicts when blocks are copied (Valkyrian Skies, WorldEdit, etc.)
        // Each block entity must have a unique UUID to ensure only one audio player per block
        private val activePlayersByUUID = mutableMapOf<String, RecordPlayerBlockEntity>()

        /**
         * Gets the record player block entity associated with the given player UUID.
         * This is used to handle stream end events from the client.
         */
        fun getBlockEntityByPlayerUUID(playerUUID: String): RecordPlayerBlockEntity? = activePlayersByUUID[playerUUID]

        /**
         * Registers a record player when it starts playing.
         */
        private fun registerPlayer(
            playerUUID: String,
            blockEntity: RecordPlayerBlockEntity,
        ) {
            activePlayersByUUID[playerUUID] = blockEntity
        }

        /**
         * Unregisters a record player when it stops playing.
         * Only unregisters if the UUID is actually registered to the given block entity.
         */
        private fun unregisterPlayer(
            playerUUID: String,
            blockEntity: RecordPlayerBlockEntity,
        ) {
            blockEntity.level?.onServer {
                activePlayersByUUID.remove(playerUUID)
                ModPackets.broadcast(AudioPlayerContextStopPacket(playerUUID))
            }
        }
    }

    private val maxPitch get() =
        ModConfigs.client.maxPitch
            .get()
            .toFloat()
    private val minPitch get() =
        ModConfigs.client.minPitch
            .get()
            .toFloat()

    private var _recordPlayerUUID: UUID? = null
    val recordPlayerUUID: UUID?
        get() = _recordPlayerUUID

    private fun ensureUUID() {
        if (_recordPlayerUUID != null) return
        if (be.level?.isClientSide != false) return
        _recordPlayerUUID = UUID.randomUUID()
        be.notifyUpdate()
    }

    private val currentPitch: Float
        get() {
            // Use static pitch (1.0f) if in static pitch mode
            if (isStaticPitchMode) {
                return 1.0f
            }

            val currSpeed = abs(be.speed)

            val minRpm = 16.0f
            val midRpm = 128.0f
            val maxRpm = 256.0f

            return when {
                currSpeed < midRpm -> {
                    val t = (currSpeed - minRpm) / (midRpm - minRpm)
                    minPitch + ((1.0f - minPitch) * t.coerceIn(0f, 1f))
                }

                currSpeed > midRpm -> {
                    val t = (currSpeed - midRpm) / (maxRpm - midRpm)
                    1.0f + ((maxPitch - 1.0f) * t.coerceIn(0f, 1f))
                }

                else -> {
                    1.0f
                }
            }
        }

    val soundRadius: Int
        get() {
            if (redstonePower <= 0) return 16
            return redstonePower.remapTo(1, 15, 4, ServerConfig.maxJukeboxSoundRange.get())
        }

    @Volatile
    private var lastActiveVolume: Float = 1f
    val currentVolume: Float
        get() {
            val playerState = audioPlayer?.state?.value
            val currentlyActiveReverberator = audioPlayer?.reverberator?.currentlyActive ?: false
            val isDecaying =
                playbackState == PlaybackState.PAUSED ||
                    playerState == PlayerState.TAILING

            if (isDecaying || (
                    currentlyActiveReverberator &&
                        playerState != PlayerState.PLAYING &&
                        playerState != PlayerState.LOADING
                )
            ) {
                return lastActiveVolume
            }

            return when {
                redstonePower <= 0 && isPauseMode -> 0.0f
                redstonePower <= 0 -> 1f
                else -> redstonePower.toFloat().remapTo(1f, 15f, 0.1f, 1.0f)
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

    val playtimeClock = PlaytimeClock()

    // Track previous playback mode to detect user changes
    private var previousPlaybackMode: RecordPlayerBlockEntity.PlaybackMode? = null

    // Track if playback ended naturally (to prevent auto-restart)
    private var playbackEndedNaturally = false

    // Flag to request restart on next tick (for redstone looping)
    private var shouldRestartOnNextTick = false

    val lazyItemHandler: LazyOptional<RecordPlayerItemHandler> = LazyOptional.of { itemHandler }
    val itemHandler = RecordPlayerItemHandler(this)
    private var pendingRecordUse = false
    private var ticksSinceLastClockSave = 0

    private val audioPlayer: AudioPlayer?
        get() {
            val uuid = _recordPlayerUUID ?: return null
            val player =
                AudioPlayerManager.getOrCreate(
                    id = uuid.toString(),
                    provider = { streamId, stream ->
                        StreamingSoundInstance.simpleFactory(
                            this,
                            stream,
                            streamId,
                            SoundEvents.EMPTY,
                        )
                    },
                    effectChainConfiguration = { player ->
                        val effects = this.getEffects()
                        // Add a pitch shift effect to handle pitch changes based on speed, at 0 index in the chain
                        if (effects.none { it is PitchShiftEffect }) {
                            this.addEffectAt(
                                0,
                                PitchShiftEffect(player.masterPitchInterpolator, scope = AudioEffect.Scope.MACHINE_CONTROLLED_PITCH),
                            )
                        }
                    },
                )

            if (player.contextKey !== this.be) {
                player.contextKey = this.be
                player.context =
                    BlockEntityAudioContext(
                        this.be,
                        { currentVolume },
                        { currentPitch },
                        { soundRadius.toFloat() },
                    )
            }

            return player
        }

    private val particleRandom: RandomSource = RandomSource.create()
    private val playerParticleJob =
        10.ticks().every {
            val player = AudioPlayerManager.get(recordPlayerUUID.toString()) ?: return@every
            if (playbackState != PlaybackState.PLAYING || this@RecordPlayerBehaviour.be.level is VirtualRenderWorld) {
                return@every
            }
            val level = this@RecordPlayerBehaviour.be.level ?: return@every
            val be = this@RecordPlayerBehaviour.be
            val pos = Vec3.atBottomCenterOf(be.blockPos).add(0.0, 1.2, 0.0)
            val displacement = particleRandom.nextInt(4) / 24f
            when (player.state.value) {
                PlayerState.LOADING -> {
                    level.addParticle(
                        ShriekParticleOption(2),
                        false,
                        pos.x,
                        pos.y,
                        pos.z,
                        0.0,
                        12.5,
                        0.0,
                    )
                }

                PlayerState.PLAYING -> {
                    level.addParticle(
                        ParticleTypes.NOTE,
                        pos.x + displacement,
                        pos.y + displacement,
                        pos.z + displacement,
                        particleRandom.nextFloat().toDouble(),
                        0.0,
                        0.0,
                    )
                }

                else -> {}
            }
        }

    override fun getType(): BehaviourType<RecordPlayerBehaviour> = BEHAVIOUR_TYPE

    val isStaticPitchMode: Boolean
        get() {
            val playbackMode = be.playbackMode.get()
            return playbackMode == RecordPlayerBlockEntity.PlaybackMode.PLAY_STATIC_PITCH ||
                playbackMode == RecordPlayerBlockEntity.PlaybackMode.PAUSE_STATIC_PITCH
        }

    private fun ensureTracking() {
        val uuid = _recordPlayerUUID?.toString() ?: return
        if (activePlayersByUUID[uuid] == null) {
            registerPlayer(uuid, be)
        }
    }

    val isPauseMode: Boolean
        get() {
            val playbackMode = be.playbackMode.get()
            return playbackMode == RecordPlayerBlockEntity.PlaybackMode.PAUSE ||
                playbackMode == RecordPlayerBlockEntity.PlaybackMode.PAUSE_STATIC_PITCH
        }

    override fun lazyTick() {
        super.lazyTick()
    }

    override fun tick() {
        super.tick()
        val level = be.level ?: return
        val blockPos = be.blockPos

        level.onServer { serverLevel ->
            ensureUUID()
            playtimeClock.tick()
            if (playtimeClock.isPlaying) {
                ticksSinceLastClockSave++
                if (ticksSinceLastClockSave >= 100) {
                    ticksSinceLastClockSave = 0
                    be.setChanged()
                }
            } else {
                ticksSinceLastClockSave = 0
            }
            ensureTracking()

            val outputStack = itemHandler.getStackInSlot(RECORD_OUTPUT_SLOT)
            if (!outputStack.isEmpty) {
                tryEjectOutputSlot(outputStack, serverLevel)
            }

            if (pendingRecordUse) {
                pendingRecordUse = false
                if (hasRecord()) {
                    handleRecordUse()
                }
            }

            val currentSpeed = abs(be.speed)
            val hasDisc = hasRecord()
            val isPowered = redstonePower > 0
            val playbackMode = be.playbackMode.get()

            // Handle restart request (for looping)
            if (shouldRestartOnNextTick) {
                shouldRestartOnNextTick = false
                if (hasDisc && currentSpeed > 0.0f) {
                    // Start playing from the beginning
                    updatePlaybackState(PlaybackState.PLAYING, setCurrentTime = true)
                    audioPlayCount += 1
                    pendingRecordUse = true
                }
                return@onServer
            }

            // Track mode changes
            val playbackModeChanged = previousPlaybackMode != playbackMode
            previousPlaybackMode = playbackMode

            when {
                !hasDisc -> {
                    if (playbackState != PlaybackState.STOPPED) {
                        updatePlaybackState(PlaybackState.STOPPED, resetTime = true)
                        audioPlayCount = 0
                        audioPlayingTitle = null
                    }
                    // Reset flags when record is removed
                    playbackEndedNaturally = false
                }

                currentSpeed == 0.0f -> {
                    // No RPM power - pause if playing
                    if (playbackState == PlaybackState.PLAYING) {
                        updatePlaybackState(PlaybackState.PAUSED, resetTime = false)
                        speedInterrupted = true
                    }
                }

                currentSpeed > 0.0f -> {
                    // Has RPM power - determine behavior based on mode and redstone

                    if (playbackMode == RecordPlayerBlockEntity.PlaybackMode.PLAY ||
                        playbackMode == RecordPlayerBlockEntity.PlaybackMode.PLAY_STATIC_PITCH
                    ) {
                        // PLAY MODE (normal or static pitch)
                        if (redstonePower == 15) {
                            currentlyLooping = true
                            // Play mode + Redstone: Always play and loop
                            playbackEndedNaturally = false
                            if (playbackState != PlaybackState.PLAYING) {
                                updatePlaybackState(PlaybackState.PLAYING, setCurrentTime = true)
                                audioPlayCount += 1
                                pendingRecordUse = true
                            }
                        } else {
                            currentlyLooping = false
                            // Play mode + No redstone: Play once then stop
                            when (playbackState) {
                                PlaybackState.STOPPED -> {
                                    // Start playing if just inserted or mode changed (but not after natural end)
                                    if (!playbackEndedNaturally) {
                                        updatePlaybackState(PlaybackState.PLAYING, setCurrentTime = true)
                                        audioPlayCount += 1
                                        pendingRecordUse = true
                                    }
                                }

                                PlaybackState.PAUSED -> {
                                    // Resume if mode changed to PLAY
                                    when {
                                        playbackModeChanged -> {
                                            updatePlaybackState(PlaybackState.PLAYING, setCurrentTime = false)
                                        }

                                        speedInterrupted -> {
                                            updatePlaybackState(PlaybackState.PLAYING, setCurrentTime = false)
                                            speedInterrupted = false
                                        }
                                    }
                                }

                                PlaybackState.PLAYING -> {
                                    // Already playing, continue
                                }
                            }
                        }
                    } else {
                        // PAUSE MODE (normal or static pitch)
                        if (isPowered) {
                            currentlyLooping = true
                            // Pause mode + Redstone: Redstone controls playback (play/loop when powered)
                            playbackEndedNaturally = false
                            if (playbackState != PlaybackState.PLAYING) {
                                // Redstone powered - start or resume playing
                                val shouldResetTime = playbackState == PlaybackState.STOPPED
                                updatePlaybackState(PlaybackState.PLAYING, setCurrentTime = shouldResetTime)
                                if (shouldResetTime) {
                                    audioPlayCount += 1
                                    pendingRecordUse = true
                                }
                            }
                        } else {
                            currentlyLooping = false
                            // Pause mode + No redstone: Stay paused (don't auto-play on insert)
                            if (playbackState == PlaybackState.PLAYING) {
                                updatePlaybackState(PlaybackState.PAUSED, resetTime = false)
                                speedInterrupted = false
                            }
                            // If STOPPED, stay STOPPED (don't auto-start)
                        }
                    }
                }
            }
        }

        level.onClient { level, virtual ->
            audioPlayer?.tick()
            if (playbackState == PlaybackState.PLAYING) {
                audioPlayer?.syncWith(playtimeClock)
                lastActiveVolume = currentVolume
            }
        }
    }

    fun handleRecordUse() {
        be.level?.onServer { level ->
            val record = getRecord()
            val result = handleRecordUse(record, RandomSource.create())

            when {
                result.shouldReplace -> {
                    result.replacementStack?.let { setRecord(it) }
                }

                result.isBroken -> {
                    setRecord(ItemStack.EMPTY)
                    val itemStack = (result as RecordUtilities.RecordUseResult.Broken).dropStack.copy()

                    val facing = be.blockState.getValue(BlockStateProperties.FACING)

                    val dropPos =
                        Vec3.atCenterOf(be.blockPos).add(
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

                    itemHandler.setStackInSlot(RECORD_OUTPUT_SLOT, itemStack)
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
            this.be
                .getBehaviour(DirectBeltInputBehaviour.TYPE)
                ?.tryExportingToBeltFunnel(stack, facing.opposite, false)
        if (funnelResult != null && funnelResult.count != stack.count) {
            itemHandler.setStackInSlot(RECORD_OUTPUT_SLOT, funnelResult)
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
                itemHandler.setStackInSlot(RECORD_OUTPUT_SLOT, remainder)
                be.notifyUpdate()
                return
            }
        }

        val dropPos =
            Vec3.atCenterOf(be.blockPos).add(
                facing.stepX * 0.7,
                facing.stepY * 0.7,
                facing.stepZ * 0.7,
            )
        itemHandler.setStackInSlot(RECORD_OUTPUT_SLOT, ItemStack.EMPTY)
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
        // Reset the flag when a new record is inserted (not when removing)
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
                updatePlaybackState(PlaybackState.STOPPED, resetTime = true)
            }

            abs(be.speed) <= 0.0f -> {
                updatePlaybackState(PlaybackState.PAUSED, resetTime = false)
            }

            else -> {
                updatePlaybackState(PlaybackState.PLAYING, setCurrentTime = true)
                audioPlayCount += 1
                pendingRecordUse = true
            }
        }
    }

    fun stopPlayer() {
        updatePlaybackState(PlaybackState.STOPPED, resetTime = true)
    }

    fun pausePlayer() {
        updatePlaybackState(PlaybackState.PAUSED, resetTime = false)
    }

    private fun updatePlaybackState(
        newState: PlaybackState,
        resetTime: Boolean = false,
        setCurrentTime: Boolean = false,
    ) {
        if (playbackState == newState && !resetTime && !setCurrentTime) {
            return
        }

        val oldState = playbackState
        playbackState = newState

        // Handle pause timing
        when (newState) {
            PlaybackState.PAUSED -> {
                if (oldState == PlaybackState.PLAYING) {
                    playtimeClock.pause()
                }
            }

            PlaybackState.PLAYING -> {
                if (oldState == PlaybackState.PAUSED) {
                    // Resumed from pause, accumulate the paused duration
                    playtimeClock.play()
                }
            }

            else -> {}
        }

        when (newState) {
            PlaybackState.PLAYING if oldState != PlaybackState.PLAYING -> {
                val uuid = _recordPlayerUUID?.toString() ?: return
                registerPlayer(uuid, be)
                playtimeClock.play()
            }

            PlaybackState.STOPPED if oldState != PlaybackState.STOPPED -> {
                val uuid = _recordPlayerUUID?.toString() ?: return
                unregisterPlayer(uuid, be)
                playtimeClock.stop()
            }

            else -> {}
        }

        be.notifyUpdate()
    }

    private fun startClientPlayer(
        currentRecord: ItemStack,
        initialPos: Double = 0.0,
    ) {
        val level = be.level ?: return
        val player = audioPlayer ?: return

        player.playFromRecord(
            currentRecord,
            initialPos,
        )
    }

    private fun resumeClientPlayer() {
        audioPlayer?.play()
    }

    private fun pauseClientPlayer() {
        audioPlayer?.pause()
    }

    private fun stopClientPlayer() {
        audioPlayer?.stop()
    }

    fun dropContent() {
        val currLevel = be.level ?: return

        val inv = SimpleContainer(itemHandler.slots)
        for (i in 0 until itemHandler.slots) {
            inv.setItem(i, itemHandler.getStackInSlot(i))
        }

        Containers.dropContents(currLevel, be.blockPos, inv)
    }

    override fun unload() {
        val uuidStr = recordPlayerUUID?.toString() ?: return

        playerParticleJob.cancel()

        if (be.isChunkUnloaded) {
            be.onServer {
                unregisterPlayer(uuidStr, be)
            }

            be.level?.onClient { _, _ ->
                AudioPlayerManager.release(uuidStr)
            }
        }

        lazyItemHandler.invalidate()
        super.unload()
    }

    override fun destroy() {
        val uuidStr = recordPlayerUUID?.toString() ?: return

        playerParticleJob.cancel()

        // Release directly on client — don't wait for the server packet,
        // which arrives too late and causes the new BE to grab the stale instance
        be.level?.onClient { _, _ ->
            AudioPlayerManager.release(uuidStr)
        }

        be.onServer {
            unregisterPlayer(uuidStr, be)
        }

        dropContent()
        super.destroy()
    }

    override fun write(
        compound: CompoundTag,
        clientPacket: Boolean,
    ) {
        compound.put("Inventory", itemHandler.serializeNBT())
        NBTHelper.writeEnum(compound, "PlaybackState", playbackState)
        _recordPlayerUUID?.let {
            compound.putUUID("RecordPlayerUUID", it)
        }

        compound.putClock(playtimeClock)

        compound.putLong("AudioPlayCount", audioPlayCount)
        compound.putBoolean("SpeedInterrupted", speedInterrupted)
        compound.putInt("RedstonePower", redstonePower)
        audioPlayingTitle?.let {
            compound.putString("AudioPlayingTitle", it)
        }
    }

    override fun read(
        compound: CompoundTag,
        clientPacket: Boolean,
    ) {
        if (compound.contains("Inventory")) {
            itemHandler.deserializeNBT(compound.getCompound("Inventory"))
        }

        if (compound.contains("RedstonePower")) {
            redstonePower = compound.getInt("RedstonePower")
        }

        if (compound.contains("RecordPlayerUUID")) {
            val loadedUUID = compound.getUUID("RecordPlayerUUID")
            _recordPlayerUUID = loadedUUID
        }

        if (compound.contains("SpeedInterrupted")) {
            speedInterrupted = compound.getBoolean("SpeedInterrupted")
        }

        compound.updateClock(playtimeClock)

        if (compound.contains("AudioPlayingTitle")) {
            audioPlayingTitle = compound.getString("AudioPlayingTitle")
        }

        if (compound.contains("PlaybackState")) {
            val newPlaybackState = NBTHelper.readEnum(compound, "PlaybackState", PlaybackState::class.java)
            val oldPlaybackState = playbackState

            be.level?.onClient { level, virtual ->
                val player = audioPlayer ?: return@onClient
                when (newPlaybackState) {
                    PlaybackState.PLAYING -> {
                        val currentRecord = getRecord()
                        if (!currentRecord.isEmpty && currentRecord.item is EtherealRecordItem) {
                            when (player.state.value) {
                                PlayerState.PAUSED -> {
                                    // Resuming from pause — let the internal clock continue
                                    resumeClientPlayer()
                                }

                                PlayerState.PLAYING -> {
                                    playtimeClock.play(player.clock.currentPlaytime)
                                }

                                else -> {
                                    // STOPPED or LOADING
                                    startClientPlayer(currentRecord, playtimeClock.currentPlaytime)
                                }
                            }
                        }
                    }

                    PlaybackState.PAUSED -> {
                        lastActiveVolume = currentVolume
                        pauseClientPlayer()
                    }

                    PlaybackState.STOPPED -> {
                        stopClientPlayer()
                    }

                    else -> {}
                }
            }
            playbackState = newPlaybackState
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
            updatePlaybackState(PlaybackState.STOPPED, resetTime = true)
            shouldRestartOnNextTick = true
        } else {
            playbackEndedNaturally = true
            updatePlaybackState(PlaybackState.STOPPED, resetTime = true)
        }
    }

    fun onAudioTitleUpdate(audioTitle: String) {
        if (audioTitle == "Unknown") return
        audioPlayingTitle = audioTitle
    }
}
