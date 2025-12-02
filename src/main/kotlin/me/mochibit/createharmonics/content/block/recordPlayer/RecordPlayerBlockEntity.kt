package me.mochibit.createharmonics.content.block.recordPlayer

import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import me.mochibit.createharmonics.audio.AudioPlayer
import me.mochibit.createharmonics.audio.AudioPlayerRegistry
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.effect.LowPassFilterEffect
import me.mochibit.createharmonics.audio.effect.ReverbEffect
import me.mochibit.createharmonics.audio.effect.VolumeEffect
import me.mochibit.createharmonics.audio.effect.pitchShift.PitchFunction
import me.mochibit.createharmonics.audio.effect.pitchShift.PitchShiftEffect
import me.mochibit.createharmonics.audio.instance.StaticSoundInstance
import me.mochibit.createharmonics.content.item.EtherealRecordItem
import me.mochibit.createharmonics.content.item.EtherealRecordItem.Companion.getAudioUrl
import me.mochibit.createharmonics.extension.onClient
import me.mochibit.createharmonics.extension.onServer
import me.mochibit.createharmonics.extension.remapTo
import net.createmod.catnip.nbt.NBTHelper
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.Containers
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.JukeboxBlock
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.items.IItemHandler
import net.minecraftforge.items.ItemStackHandler
import java.util.*
import kotlin.concurrent.Volatile
import kotlin.math.abs

/**
 * Block entity for the Record Player block.
 *
 * Manages playback of Ethereal Records with dynamic audio effects based on rotational speed.
 * Features include:
 * - Speed-based pitch shifting
 * - Audio effects (volume, low-pass filter, reverb)
 * - Automatic pause/resume based on rotation
 * - Redstone control support
 * - Record inventory management
 *
 * @param type The block entity type
 * @param pos The block position
 * @param state The block state
 */
open class RecordPlayerBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState,
) : KineticBlockEntity(type, pos, state) {

    /**
     * Represents the current playback state of the record player.
     */
    enum class PlaybackState {
        /** Audio is currently playing */
        PLAYING,

        /** No record or playback stopped */
        STOPPED,

        /** Playback paused due to insufficient speed */
        PAUSED,

        /** Playback manually paused by redstone signal */
        MANUALLY_PAUSED;
    }

    var playerUUID: UUID = UUID.randomUUID()
        private set

    protected val currentPitch: Float
        get() {
            val currSpeed = abs(this.speed)
            return currSpeed.remapTo(16.0f, 256.0f, MIN_PITCH, MAX_PITCH)
        }

    @Volatile
    var playbackState: PlaybackState = PlaybackState.STOPPED
        private set

    @Volatile
    var playTime: Long = 0

    companion object {
        const val RECORD_SLOT = 0
        const val MIN_PITCH = 0.5f
        const val MAX_PITCH = 2.0f
        const val PITCH_TRANSITION_TIME = 0.5 // seconds
        const val VOLUME_LEVEL = 0.8f
        const val LOWPASS_CUTOFF = 3000f
        const val REVERB_ROOM_SIZE = 0.5f
        const val REVERB_DAMPING = 0.2f
        const val REVERB_WET_MIX = 0.8f
        const val SOUND_RADIUS = 64
    }

    /**
     * Audio player instance for this record player.
     * Lazily initialized and registered globally.
     */
    private val audioPlayer: AudioPlayer by lazy {
        AudioPlayerRegistry.getOrCreatePlayer(playerUUID.toString()) {
            AudioPlayer(
                soundInstanceProvider = { streamId, stream ->
                    StaticSoundInstance(
                        stream,
                        streamId,
                        this.worldPosition,
                        SOUND_RADIUS,
                    )
                },
                playerId = playerUUID.toString()
            )
        }
    }

    /**
     * Inventory handler for storing a single Ethereal Record.
     */
    val inventoryHandler = object : ItemStackHandler(1) {
        override fun isItemValid(slot: Int, stack: ItemStack): Boolean {
            return stack.item is EtherealRecordItem
        }

        override fun onContentsChanged(slot: Int) {
            super.onContentsChanged(slot)
            val hasDisc = !getStackInSlot(RECORD_SLOT).isEmpty
            level?.setBlockAndUpdate(
                worldPosition,
                blockState.setValue(JukeboxBlock.HAS_RECORD, hasDisc)
            )
            notifyUpdate()
        }
    }

    protected val lazyInventoryHandler: LazyOptional<IItemHandler> =
        LazyOptional.of { inventoryHandler }

    /**
     * Pitch function that smoothly transitions based on the current rotational speed.
     */
    val speedBasedPitchFunction = PitchFunction.smoothedRealTime(
        sourcePitchFunction = PitchFunction.custom { _ -> currentPitch },
        transitionTimeSeconds = PITCH_TRANSITION_TIME
    )

    /**
     * Start playback if conditions are met (has record and sufficient speed).
     */
    fun startPlayer() {
        when {
            !hasRecord() -> {
                updatePlaybackState(PlaybackState.STOPPED, resetTime = true)
            }

            abs(this.speed) <= 0.0f -> {
                updatePlaybackState(PlaybackState.PAUSED, resetTime = true)
            }

            else -> {
                updatePlaybackState(PlaybackState.PLAYING, setCurrentTime = true)
            }
        }
    }

    /**
     * Stop playback and reset play time.
     */
    fun stopPlayer() {
        updatePlaybackState(PlaybackState.STOPPED, resetTime = true)
    }

    /**
     * Manually pause playback (typically by redstone signal).
     */
    fun pausePlayer() {
        updatePlaybackState(PlaybackState.MANUALLY_PAUSED, resetTime = false)
    }

    /**
     * Internal helper to update playback state only when it changes.
     */
    private fun updatePlaybackState(
        newState: PlaybackState,
        resetTime: Boolean = false,
        setCurrentTime: Boolean = false
    ) {
        if (playbackState == newState && !resetTime && !setCurrentTime) {
            return
        }

        playbackState = newState

        when {
            resetTime -> playTime = 0
            setCurrentTime -> playTime = System.currentTimeMillis()
        }

        notifyUpdate()
    }

    /**
     * Start audio playback on the client with configured effects.
     * @param audioUrl The URL of the audio to play
     */
    protected fun startClientPlayer(audioUrl: String) {

        val offsetSeconds = if (playTime > 0) {
            (System.currentTimeMillis() - this.playTime) / 1000.0
        } else {
            0.0
        }

        audioPlayer.play(
            audioUrl,
            EffectChain(
                listOf(
                    PitchShiftEffect(speedBasedPitchFunction),
                    VolumeEffect(VOLUME_LEVEL),
                    LowPassFilterEffect(cutoffFrequency = LOWPASS_CUTOFF),
                    ReverbEffect(
                        roomSize = REVERB_ROOM_SIZE,
                        damping = REVERB_DAMPING,
                        wetMix = REVERB_WET_MIX
                    )
                )
            ),
            offsetSeconds
        )
    }

    /**
     * Resume paused audio playback on the client.
     */
    protected fun resumeClientPlayer() {
        audioPlayer.resume()
    }

    /**
     * Pause audio playback on the client.
     */
    protected fun pauseClientPlayer() {
        audioPlayer.pause()
    }

    /**
     * Stop audio playback on the client.
     */
    protected fun stopClientPlayer() {
        audioPlayer.stop()
    }

    override fun remove() {
        onClient {
            // Properly dispose of audio player when block entity is removed
            // Only dispose if it was actually used (player is registered)
            if (AudioPlayerRegistry.containsStream(playerUUID.toString())) {
                audioPlayer.dispose()
            }
        }
        super.remove()
    }

    override fun destroy() {
        stopPlayer()
        dropContent()
        super.destroy()
    }

    /**
     * Drop all inventory contents into the world.
     */
    fun dropContent() {
        val currLevel = this.level ?: return
        val inv = SimpleContainer(inventoryHandler.slots)
        for (i in 0 until inventoryHandler.slots) {
            inv.setItem(i, inventoryHandler.getStackInSlot(i))
        }

        Containers.dropContents(currLevel, this.worldPosition, inv)
    }

    /**
     * Insert a record into the player.
     * @param discItem The record item stack to insert
     * @param autoPlay Whether to automatically start playing if speed is sufficient
     * @return true if the record was successfully inserted
     */
    fun insertRecord(discItem: ItemStack, autoPlay: Boolean = false): Boolean {
        if (hasRecord()) return false
        inventoryHandler.insertItem(RECORD_SLOT, discItem.copy(), false)

        if (autoPlay && abs(this.speed) > 0.0f) {
            playbackState = PlaybackState.PLAYING
            notifyUpdate()
        }

        return true
    }

    /**
     * Remove and return the current record.
     * @return The record item stack, or null if no record is present
     */
    fun popRecord(): ItemStack? {
        if (!hasRecord()) return null
        val item = inventoryHandler.extractItem(RECORD_SLOT, 1, false)
        return item
    }

    /**
     * Get a copy of the current record without removing it.
     * @return A copy of the record item stack
     */
    fun getRecord(): ItemStack {
        return inventoryHandler.getStackInSlot(RECORD_SLOT).copy()
    }

    /**
     * Get the current record as an EtherealRecordItem.
     * @return The EtherealRecordItem, or null if no record or not an EtherealRecordItem
     */
    fun getRecordItem(): EtherealRecordItem? {
        val recordStack = getRecord()
        if (recordStack.isEmpty || recordStack.item !is EtherealRecordItem) return null
        return recordStack.item as EtherealRecordItem
    }

    /**
     * Check if a record is currently in the player.
     * @return true if a record is present
     */
    fun hasRecord(): Boolean {
        return !inventoryHandler.getStackInSlot(RECORD_SLOT).isEmpty
    }

    override fun <T : Any?> getCapability(
        cap: Capability<T?>,
        side: Direction?
    ): LazyOptional<T?> {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return lazyInventoryHandler.cast()
        }
        return super.getCapability(cap, side)
    }

    override fun tick() {
        super.tick()

        onServer {
            val currentSpeed = abs(this.speed)
            val hasDisc = hasRecord()
            val isPowered = level?.hasNeighborSignal(worldPosition) ?: false

            when {
                !hasDisc -> {
                    if (playbackState != PlaybackState.STOPPED) {
                        updatePlaybackState(PlaybackState.STOPPED, resetTime = true)
                    }
                }

                currentSpeed == 0.0f -> {
                    if (playbackState == PlaybackState.PLAYING) {
                        updatePlaybackState(PlaybackState.PAUSED, resetTime = false)
                    }
                }

                currentSpeed > 0.0f -> {
                    if (isPowered) {
                        if (playbackState != PlaybackState.PLAYING) {
                            updatePlaybackState(PlaybackState.PLAYING, setCurrentTime = true)
                        }
                    } else {
                        if (playbackState == PlaybackState.PAUSED) {
                            updatePlaybackState(PlaybackState.PLAYING, setCurrentTime = true)
                        }
                    }
                }
            }
        }
    }

    override fun write(compound: CompoundTag, clientPacket: Boolean) {
        super.write(compound, clientPacket)
        compound.put("Inventory", inventoryHandler.serializeNBT())
        NBTHelper.writeEnum(compound, "playbackState", playbackState)
        compound.putUUID("playerUUID", playerUUID)
        compound.putLong("playTime", playTime)
    }

    override fun read(compound: CompoundTag, clientPacket: Boolean) {
        super.read(compound, clientPacket)
        if (compound.contains("Inventory")) {
            inventoryHandler.deserializeNBT(compound.getCompound("Inventory"))
        }

        if (compound.contains("playerUUID")) {
            playerUUID = compound.getUUID("playerUUID")
        }

        // Read playTime BEFORE processing playbackState
        // This ensures the offset calculation has valid data
        if (compound.contains("playTime")) {
            playTime = compound.getLong("playTime")
        }

        if (compound.contains("playbackState")) {
            val newPlaybackState = NBTHelper.readEnum(compound, "playbackState", PlaybackState::class.java)
            val oldPlaybackState = playbackState
            onClient {
                // Only act if the state actually changed
                if (oldPlaybackState != newPlaybackState) {
                    when (newPlaybackState) {
                        PlaybackState.PLAYING -> {
                            // Start or restart playback with correct offset
                            val currentRecord = getRecord()
                            if (!currentRecord.isEmpty && currentRecord.item is EtherealRecordItem) {
                                val audioUrl = getAudioUrl(currentRecord)
                                if (!audioUrl.isNullOrEmpty()) {
                                    startClientPlayer(audioUrl)
                                }
                            }
                        }

                        PlaybackState.PAUSED, PlaybackState.MANUALLY_PAUSED -> {
                            pauseClientPlayer()
                        }

                        PlaybackState.STOPPED -> {
                            stopClientPlayer()
                        }
                    }
                }
            }
            playbackState = newPlaybackState
        }
    }


}