package me.mochibit.createharmonics.content.kinetics.recordPlayer

import com.simibubi.create.Create
import com.simibubi.create.content.contraptions.AbstractContraptionEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.audio.AudioPlayer
import me.mochibit.createharmonics.audio.AudioPlayerRegistry
import me.mochibit.createharmonics.audio.comp.PitchSupplierInterpolated
import me.mochibit.createharmonics.audio.comp.SoundEventComposition
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.effect.getStreamDirectly
import me.mochibit.createharmonics.audio.instance.SimpleStreamSoundInstance
import me.mochibit.createharmonics.audio.stream.Ogg2PcmInputStream
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerItemHandler.Companion.MAIN_RECORD_SLOT
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerMovementBehaviour.Companion.PLAYER_UUID_KEY
import me.mochibit.createharmonics.content.records.EtherealRecordItem
import me.mochibit.createharmonics.content.records.EtherealRecordItem.Companion.getAudioUrl
import me.mochibit.createharmonics.content.records.EtherealRecordItem.Companion.playFromRecord
import me.mochibit.createharmonics.content.records.RecordCraftingHandler
import me.mochibit.createharmonics.extension.onClient
import me.mochibit.createharmonics.extension.onServer
import me.mochibit.createharmonics.extension.remapTo
import me.mochibit.createharmonics.network.packet.AudioPlayerContextStopPacket
import me.mochibit.createharmonics.registry.ModConfigurations
import me.mochibit.createharmonics.registry.ModPackets
import net.createmod.catnip.nbt.NBTHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.particle.NoteParticle
import net.minecraft.core.Direction
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
import net.minecraft.world.item.Items
import net.minecraft.world.item.RecordItem
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.network.PacketDistributor
import java.util.UUID
import kotlin.math.abs

class RecordPlayerBehaviour(
    val be: RecordPlayerBlockEntity,
) : BlockEntityBehaviour(be) {
    enum class PlaybackState {
        PLAYING,
        STOPPED,
        PAUSED,
        MANUALLY_PAUSED,
    }

    companion object {
        @JvmStatic
        val BEHAVIOUR_TYPE = BehaviourType<RecordPlayerBehaviour>()

        // Static tracking for active record players by their audio player UUID
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
            activePlayersByUUID.putIfAbsent(playerUUID, blockEntity)
        }

        /**
         * Unregisters a record player when it stops playing.
         */
        private fun unregisterPlayer(playerUUID: String) {
            activePlayersByUUID.remove(playerUUID)
            ModPackets.channel.send(
                PacketDistributor.ALL.noArg(),
                AudioPlayerContextStopPacket(playerUUID),
            )
        }
    }

    private val maxPitch =
        ModConfigurations.client.maxPitch
            .get()
            .toFloat()
    private val minPitch =
        ModConfigurations.client.minPitch
            .get()
            .toFloat()

    var recordPlayerUUID: UUID = UUID.randomUUID()
        private set

    private val currentPitch: Float
        get() {
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

    // TODO: make this adjustable with a wrench
    var soundRadius: Int = 32

    @Volatile
    var playbackState: PlaybackState = PlaybackState.STOPPED
        private set

    @Volatile
    var playTime: Long = 0
        private set

    @Volatile
    var pauseStartTime: Long = 0
        private set

    @Volatile
    var totalPausedTime: Long = 0
        private set

    @Volatile
    var audioPlayCount: Long = 0
        private set

    @Volatile
    var audioPlayingTitle: String? = null
        private set

    // Track previous playback mode to detect user changes
    private var previousPlaybackMode: RecordPlayerBlockEntity.PlaybackMode? = null

    // Track if playback ended naturally (to prevent auto-restart)
    private var playbackEndedNaturally = false

    // Flag to request restart on next tick (for redstone looping)
    private var shouldRestartOnNextTick = false

    val itemHandler = RecordPlayerItemHandler(this, 1)
    val lazyItemHandler: LazyOptional<RecordPlayerItemHandler> = LazyOptional.of { itemHandler }

    val pitchSupplierInterpolated = PitchSupplierInterpolated({ currentPitch }, 500)

    private val audioPlayer: AudioPlayer
        get() =
            AudioPlayerRegistry.getOrCreatePlayer(recordPlayerUUID.toString()) {
                AudioPlayer(
                    soundInstanceProvider = { streamId, stream ->
                        SimpleStreamSoundInstance(
                            stream,
                            streamId,
                            SoundEvents.EMPTY,
                            { be.blockPos },
                            radiusSupplier = { soundRadius },
                            pitchSupplier = { pitchSupplierInterpolated.getPitch() },
                        )
                    },
                    playerId = recordPlayerUUID.toString(),
                )
            }

    override fun getType(): BehaviourType<RecordPlayerBehaviour> = BEHAVIOUR_TYPE

    override fun tick() {
        super.tick()

        val level = be.level ?: return
        level.onServer {
            val currentSpeed = abs(be.speed)
            val hasDisc = hasRecord()
            val isPowered = level.hasNeighborSignal(be.blockPos)
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
                    }
                }

                currentSpeed > 0.0f -> {
                    // Has RPM power - determine behavior based on mode and redstone

                    if (playbackMode == RecordPlayerBlockEntity.PlaybackMode.PLAY) {
                        // PLAY MODE
                        if (isPowered) {
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

                                PlaybackState.PAUSED, PlaybackState.MANUALLY_PAUSED -> {
                                    // Resume if mode changed to PLAY
                                    if (playbackModeChanged) {
                                        updatePlaybackState(PlaybackState.PLAYING, setCurrentTime = false)
                                    }
                                }

                                PlaybackState.PLAYING -> {
                                    // Already playing, continue
                                }
                            }
                        }
                    } else {
                        // PAUSE MODE
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
                                updatePlaybackState(PlaybackState.MANUALLY_PAUSED, resetTime = false)
                            }
                            // If STOPPED, stay STOPPED (don't auto-start)
                        }
                    }
                }
            }
        }
    }

    override fun lazyTick() {
        this.be.level?.onClient { level, virtual ->
            val pos = Vec3.atBottomCenterOf(be.blockPos).add(0.0, 1.2, 0.0)
            val displacement = level.random.nextInt(4) / 24f
            when (audioPlayer.state) {
                AudioPlayer.PlayState.LOADING -> {
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

                AudioPlayer.PlayState.PLAYING -> {
                    level.addParticle(
                        ParticleTypes.NOTE,
                        pos.x + displacement,
                        pos.y + displacement,
                        pos.z + displacement,
                        level.random.nextFloat().toDouble(),
                        0.0,
                        0.0,
                    )
                }

                else -> {
                }
            }
        }
    }

    fun handleRecordUse() {
        be.level?.onServer { level ->
            val record = getRecord()
            val result = EtherealRecordItem.handleRecordUse(record, RandomSource.create())

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
                        val itemStack = (result as EtherealRecordItem.Companion.RecordUseResult.Broken).dropStack.copy()
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
        updatePlaybackState(PlaybackState.MANUALLY_PAUSED, resetTime = false)
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

        when {
            resetTime -> {
                playTime = 0
                pauseStartTime = 0
                totalPausedTime = 0
            }

            setCurrentTime -> {
                playTime = System.currentTimeMillis()
                pauseStartTime = 0
                totalPausedTime = 0
            }
        }

        // Handle pause timing
        when (newState) {
            PlaybackState.PAUSED, PlaybackState.MANUALLY_PAUSED -> {
                if (oldState == PlaybackState.PLAYING) {
                    // Started pausing, record when
                    pauseStartTime = System.currentTimeMillis()
                }
            }

            PlaybackState.PLAYING -> {
                if (oldState == PlaybackState.PAUSED || oldState == PlaybackState.MANUALLY_PAUSED) {
                    // Resumed from pause, accumulate the paused duration
                    if (pauseStartTime > 0) {
                        totalPausedTime += (System.currentTimeMillis() - pauseStartTime)
                        pauseStartTime = 0
                    }
                }
            }

            else -> {}
        }

        when (newState) {
            PlaybackState.PLAYING if oldState != PlaybackState.PLAYING -> {
                registerPlayer(recordPlayerUUID.toString(), be)
            }

            PlaybackState.STOPPED if oldState != PlaybackState.STOPPED -> {
                unregisterPlayer(recordPlayerUUID.toString())
            }

            else -> {}
        }

        be.notifyUpdate()
    }

    private fun startClientPlayer(currentRecord: ItemStack) {
        val offsetSeconds =
            if (playTime > 0) {
                // Calculate elapsed time minus any accumulated pause time
                val elapsedTime = System.currentTimeMillis() - playTime
                val adjustedTime = elapsedTime - totalPausedTime
                adjustedTime / 1000.0
            } else {
                0.0
            }

        audioPlayer.playFromRecord(
            currentRecord,
            offsetSeconds,
        ) {
            pitchSupplierInterpolated.getPitch()
        }
    }

    private fun resumeClientPlayer() {
        audioPlayer.resume()
    }

    private fun pauseClientPlayer() {
        audioPlayer.pause()
    }

    private fun stopClientPlayer() {
        audioPlayer.stopSoundImmediately()
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
        be.onServer {
            unregisterPlayer(recordPlayerUUID.toString())
        }
        be.onClient {
            audioPlayer.stopSoundImmediately()
        }
        lazyItemHandler.invalidate()
        super.unload()
    }

    override fun destroy() {
        stopPlayer()
        dropContent()
        unregisterPlayer(recordPlayerUUID.toString())
        be.onClient {
            audioPlayer.stopSoundImmediately()
        }
        super.destroy()
    }

    override fun write(
        compound: CompoundTag,
        clientPacket: Boolean,
    ) {
        compound.put("Inventory", itemHandler.serializeNBT())
        NBTHelper.writeEnum(compound, "PlaybackState", playbackState)
        compound.putUUID("RecordPlayerUUID", recordPlayerUUID)
        compound.putLong("PlayTime", playTime)
        compound.putLong("PauseStartTime", pauseStartTime)
        compound.putLong("TotalPausedTime", totalPausedTime)
        compound.putLong("AudioPlayCount", audioPlayCount)
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

        if (compound.contains("RecordPlayerUUID")) {
            recordPlayerUUID = compound.getUUID("RecordPlayerUUID")
            if (!clientPacket) {
                registerPlayer(recordPlayerUUID.toString(), be)
            }
        }

        if (compound.contains("PlayTime")) {
            playTime = compound.getLong("PlayTime")
        }

        if (compound.contains("PauseStartTime")) {
            pauseStartTime = compound.getLong("PauseStartTime")
        }

        if (compound.contains("TotalPausedTime")) {
            totalPausedTime = compound.getLong("TotalPausedTime")
        }

        if (compound.contains("AudioPlayingTitle")) {
            audioPlayingTitle = compound.getString("AudioPlayingTitle")
        }

        if (compound.contains("AudioPlayCount")) {
            audioPlayCount = compound.getLong("AudioPlayCount")
        }

        if (compound.contains("PlaybackState")) {
            val newPlaybackState = NBTHelper.readEnum(compound, "PlaybackState", PlaybackState::class.java)
            val oldPlaybackState = playbackState

            be.level?.onClient { level, virtual ->
                if (oldPlaybackState != newPlaybackState) {
                    when (newPlaybackState) {
                        PlaybackState.PLAYING -> {
                            val currentRecord = getRecord()
                            if (!currentRecord.isEmpty && currentRecord.item is EtherealRecordItem) {
                                // Check the actual audio player state to decide whether to start or resume
                                if (audioPlayer.state == AudioPlayer.PlayState.PAUSED) {
                                    resumeClientPlayer()
                                } else {
                                    // Player is stopped or not initialized - start from offset
                                    startClientPlayer(currentRecord)
                                }
                            }
                        }

                        PlaybackState.PAUSED, PlaybackState.MANUALLY_PAUSED -> {
                            // Only pause if there's something playing
                            if (audioPlayer.state == AudioPlayer.PlayState.PLAYING) {
                                pauseClientPlayer()
                            }
                        }

                        PlaybackState.STOPPED -> {
                            stopClientPlayer()
                        }

                        else -> {}
                    }
                }
            }
            playbackState = newPlaybackState
        }
    }

    fun onPlaybackEnd(endedPlayerId: String) {
        val level = be.level ?: return
        val isPowered = level.hasNeighborSignal(be.blockPos)

        // Determine if we should loop:
        // Loop if redstone powered (works for both Play and Pause modes)
        // Don't loop if no redstone
        val shouldLoop = isPowered

        if (shouldLoop) {
            // Loop: stop and flag for immediate restart
            playbackEndedNaturally = false
            updatePlaybackState(PlaybackState.STOPPED, resetTime = true)
            shouldRestartOnNextTick = true
        } else {
            // Don't loop: stop after one play
            playbackEndedNaturally = true
            stopPlayer()
        }
    }

    fun onAudioTitleUpdate(audioTitle: String) {
        if (audioTitle == "Unknown") return
        audioPlayingTitle = audioTitle
    }
}
