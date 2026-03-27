package me.mochibit.createharmonics.content.kinetics.recordPlayer

import com.simibubi.create.content.contraptions.render.ClientContraption
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld
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
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerItemHandler.Companion.MAIN_RECORD_SLOT
import me.mochibit.createharmonics.content.records.EtherealRecordItem
import me.mochibit.createharmonics.content.records.RecordUtilities
import me.mochibit.createharmonics.content.records.RecordUtilities.handleRecordUse
import me.mochibit.createharmonics.content.records.RecordUtilities.playFromRecord
import me.mochibit.createharmonics.foundation.async.every
import me.mochibit.createharmonics.foundation.extension.getManagingShip
import me.mochibit.createharmonics.foundation.extension.onClient
import me.mochibit.createharmonics.foundation.extension.onServer
import me.mochibit.createharmonics.foundation.extension.remapTo
import me.mochibit.createharmonics.foundation.extension.ticks
import me.mochibit.createharmonics.foundation.network.packet.AudioPlayerContextStopPacket
import me.mochibit.createharmonics.foundation.registry.ModPackets
import me.mochibit.createharmonics.foundation.services.contentService
import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplierInterpolated
import net.createmod.catnip.nbt.NBTHelper
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.ShriekParticleOption
import net.minecraft.nbt.CompoundTag
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
import java.io.InputStream
import java.util.UUID
import kotlin.math.abs

// TODO: Refactor the track logic to be cleaner and reusable with other behaviours too
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
    val recordPlayerUUID: UUID
        get() {
            return _recordPlayerUUID ?: UUID.randomUUID().also {
                _recordPlayerUUID = it
                be.level?.onServer {
                    be.notifyUpdate()
                }
            }
        }

    private val currentPitch: Float
        get() {
            // Use static pitch (1.0f) if in static pitch mode
            if (isStaticPitchMode()) {
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

    val currentVolume: Float
        get() {
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

    val playtimeClock = PlaytimeClock()

    // Track previous playback mode to detect user changes
    private var previousPlaybackMode: RecordPlayerBlockEntity.PlaybackMode? = null

    // Track if playback ended naturally (to prevent auto-restart)
    private var playbackEndedNaturally = false

    // Flag to request restart on next tick (for redstone looping)
    private var shouldRestartOnNextTick = false

    val itemHandler = RecordPlayerItemHandler(this, 1)
    val lazyItemHandler: LazyOptional<RecordPlayerItemHandler> = LazyOptional.of { itemHandler }

    val pitchSupplierInterpolated = FloatSupplierInterpolated({ currentPitch }, 500)
    val volumeSupplierInterpolated = FloatSupplierInterpolated({ currentVolume }, 500)
    val radiusSupplierInterpolated = FloatSupplierInterpolated({ soundRadius.toFloat() }, 500)

    private val underwaterEffect = EffectPreset.UnderwaterFilter()

    private var ticksSinceLastClockSave = 0

    private val audioPlayer: AudioPlayer
        get() =
            AudioPlayerManager.getOrCreate(
                id = recordPlayerUUID.toString(),
                provider = { streamId, stream ->
                    contentService.streamingSoundInstanceFactory(
                        stream,
                        streamId,
                        SoundEvents.EMPTY,
                        posSupplier = { be.blockPos },
                        radiusSupplier = radiusSupplierInterpolated,
                        volumeSupplier = volumeSupplierInterpolated,
                    )
                },
                effectChainConfiguration = {
                    val effects = this.getEffects()
                    // Add a pitch shift effect to handle pitch changes based on speed, at 0 index in the chain
                    if (effects.none { it is PitchShiftEffect }) {
                        this.addEffectAt(
                            0,
                            PitchShiftEffect(pitchSupplierInterpolated, scope = AudioEffect.Scope.MACHINE_CONTROLLED_PITCH),
                        )
                    }
                },
            )

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

    private fun isStaticPitchMode(): Boolean {
        val playbackMode = be.playbackMode.get()
        return playbackMode == RecordPlayerBlockEntity.PlaybackMode.PLAY_STATIC_PITCH ||
            playbackMode == RecordPlayerBlockEntity.PlaybackMode.PAUSE_STATIC_PITCH
    }

    private fun ensureTracking() {
        if (activePlayersByUUID[recordPlayerUUID.toString()] == null) {
            registerPlayer(recordPlayerUUID.toString(), be)
        }
    }

    val isPauseMode: Boolean
        get() {
            val playbackMode = be.playbackMode.get()
            return playbackMode == RecordPlayerBlockEntity.PlaybackMode.PAUSE ||
                playbackMode == RecordPlayerBlockEntity.PlaybackMode.PAUSE_STATIC_PITCH
        }

    override fun tick() {
        super.tick()

        val level = be.level ?: return

        level.onServer {
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
                    handleRecordUse()
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
                            // Play mode + Redstone: Always play and loop
                            playbackEndedNaturally = false
                            if (playbackState != PlaybackState.PLAYING) {
                                updatePlaybackState(PlaybackState.PLAYING, setCurrentTime = true)
                                audioPlayCount += 1
                                handleRecordUse()
                            }
                        } else {
                            // Play mode + No redstone: Play once then stop
                            when (playbackState) {
                                PlaybackState.STOPPED -> {
                                    // Start playing if just inserted or mode changed (but not after natural end)
                                    if (!playbackEndedNaturally) {
                                        updatePlaybackState(PlaybackState.PLAYING, setCurrentTime = true)
                                        audioPlayCount += 1
                                        handleRecordUse()
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

                                else -> {}
                            }
                        }
                    } else {
                        // PAUSE MODE (normal or static pitch)
                        if (isPowered) {
                            // Pause mode + Redstone: Redstone controls playback (play/loop when powered)
                            playbackEndedNaturally = false
                            if (playbackState != PlaybackState.PLAYING) {
                                // Redstone powered - start or resume playing
                                val shouldResetTime = playbackState == PlaybackState.STOPPED
                                updatePlaybackState(PlaybackState.PLAYING, setCurrentTime = shouldResetTime)
                                if (shouldResetTime) {
                                    audioPlayCount += 1
                                    handleRecordUse()
                                }
                            }
                        } else {
                            // Pause mode + No redstone: Stay paused (don't auto-play on insert)
                            if (playbackState == PlaybackState.PLAYING) {
                                updatePlaybackState(PlaybackState.PAUSED, resetTime = false)
                            }
                            // If STOPPED, stay STOPPED (don't auto-start)
                        }
                    }
                }
            }
        }

        level.onClient { level, virtual ->
            audioPlayer.tick()
            underwaterEffect.update(audioPlayer, be.blockPos, level)
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

                    val facing = be.blockState.getValue(BlockStateProperties.FACING)

                    val dropPos =
                        Vec3.atCenterOf(be.blockPos).add(
                            facing.stepX * 0.7,
                            facing.stepY * 0.7,
                            facing.stepZ * 0.7,
                        )

                    for (i in 0 until itemHandler.slots) {
                        val itemStack = (result as RecordUtilities.RecordUseResult.Broken).dropStack.copy()
                        val itemEntity = ItemEntity(level, dropPos.x, dropPos.y, dropPos.z, itemStack)

                        // Launch in the facing direction
                        itemEntity.setDeltaMovement(
                            facing.stepX * 0.3,
                            facing.stepY * 0.3,
                            facing.stepZ * 0.3,
                        )

                        level.addFreshEntity(itemEntity)
                    }

                    level.playSound(null, be.blockPos, SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, .7f, 1.7f)
                    level.playSound(null, be.blockPos, SoundEvents.SMALL_AMETHYST_BUD_BREAK, SoundSource.PLAYERS)
                }
            }
        }
    }

    fun redstonePowerChanged(power: Int) {
        redstonePower = power
        be.notifyUpdate()
    }

    fun hasRecord(): Boolean = !itemHandler.getStackInSlot(MAIN_RECORD_SLOT).isEmpty

    fun insertRecord(discItem: ItemStack): Boolean {
        if (hasRecord()) return false
        itemHandler.insertItem(MAIN_RECORD_SLOT, discItem.copy(), false)
        playbackEndedNaturally = false // Allow the newly inserted record to play
        return true
    }

    fun popRecord(): ItemStack? {
        if (!hasRecord()) return null
        val item = itemHandler.extractItem(MAIN_RECORD_SLOT, 1, false)
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
                handleRecordUse()
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
//                    playtimeClock.play()
                }
            }

            else -> {}
        }

        when (newState) {
            PlaybackState.PLAYING if oldState != PlaybackState.PLAYING -> {
                registerPlayer(recordPlayerUUID.toString(), be)
//                playtimeClock.play()
            }

            PlaybackState.STOPPED if oldState != PlaybackState.STOPPED -> {
                unregisterPlayer(recordPlayerUUID.toString(), be)
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

        audioPlayer.playFromRecord(
            currentRecord,
            { pitchSupplierInterpolated.getValue() },
            { radiusSupplierInterpolated.getValue() },
            { volumeSupplierInterpolated.getValue() },
            initialPos,
        )

        underwaterEffect.update(audioPlayer, be.blockPos, level)
    }

    private fun resumeClientPlayer() {
        audioPlayer.play()
    }

    private fun pauseClientPlayer() {
        audioPlayer.pause()
    }

    private fun stopClientPlayer() {
        audioPlayer.stop()
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
        val uuidStr = recordPlayerUUID.toString()

        playerParticleJob.cancel()

        be.onServer {
            unregisterPlayer(uuidStr, be)
        }

        // Release directly on client — don't wait for the server packet,
        // which arrives too late and causes the new BE to grab the stale instance
        be.level?.onClient { _, _ ->
            AudioPlayerManager.release(uuidStr)
        }

        lazyItemHandler.invalidate()
        super.unload()
    }

    override fun destroy() {
        val uuidStr = recordPlayerUUID.toString()

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
                when (newPlaybackState) {
                    PlaybackState.PLAYING -> {
                        val currentRecord = getRecord()
                        if (!currentRecord.isEmpty && currentRecord.item is EtherealRecordItem) {
                            when (audioPlayer.state.value) {
                                PlayerState.PAUSED -> {
                                    // Resuming from pause — let the internal clock continue
                                    resumeClientPlayer()
                                }

                                PlayerState.PLAYING -> {
                                    audioPlayer.syncWith(playtimeClock)
                                }

                                else -> {
                                    // STOPPED or LOADING
                                    startClientPlayer(currentRecord, playtimeClock.currentPlaytime)
                                }
                            }
                        }
                    }

                    PlaybackState.PAUSED -> {
                        if (audioPlayer.state.value == PlayerState.PLAYING) {
                            pauseClientPlayer()
                        }
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

        if (clientPacket && be.level?.isClientSide == true) {
            underwaterEffect.update(audioPlayer, be.blockPos, be.level!!)
        }
    }

    /**
     * This function starts the current clock, accurately tracking the time between players
     * Only the first start is considered
     */
    fun onStartClockReceived() {
        if (playtimeClock.isPlaying) return
        playtimeClock.play()
    }

    fun onPlaybackEnd(
        endedPlayerId: String,
        failure: Boolean = false,
    ) {
        if (failure) return
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
