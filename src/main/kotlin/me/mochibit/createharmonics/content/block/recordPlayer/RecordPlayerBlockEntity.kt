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
 * Manages playback of Ethereal Records with dynamic audio effects based on rotational speed.
 * Supports speed-based pitch shifting, automatic pause/resume, and redstone control.
 */
open class RecordPlayerBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState,
) : KineticBlockEntity(type, pos, state) {

    enum class PlaybackState {
        PLAYING,
        STOPPED,
        PAUSED,
        MANUALLY_PAUSED
    }

    var recordPlayerUUID: UUID = UUID.randomUUID()
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
        const val PITCH_TRANSITION_TIME = 0.5
        const val VOLUME_LEVEL = 0.8f
        const val LOWPASS_CUTOFF = 3000f
        const val REVERB_ROOM_SIZE = 0.5f
        const val REVERB_DAMPING = 0.2f
        const val REVERB_WET_MIX = 0.8f
        const val SOUND_RADIUS = 64

        // Static tracking for active record players by their audio player UUID
        private val activePlayersByUUID = mutableMapOf<String, RecordPlayerBlockEntity>()

        /**
         * Gets the record player block entity associated with the given player UUID.
         * This is used to handle stream end events from the client.
         */
        fun getBlockEntityByPlayerUUID(playerUUID: String): RecordPlayerBlockEntity? {
            return activePlayersByUUID[playerUUID]
        }

        /**
         * Registers a record player when it starts playing.
         */
        private fun registerPlayer(playerUUID: String, blockEntity: RecordPlayerBlockEntity) {
            activePlayersByUUID[playerUUID] = blockEntity
        }

        /**
         * Unregisters a record player when it stops playing.
         */
        private fun unregisterPlayer(playerUUID: String) {
            activePlayersByUUID.remove(playerUUID)
        }
    }

    private val audioPlayer: AudioPlayer by lazy {
        AudioPlayerRegistry.getOrCreatePlayer(recordPlayerUUID.toString()) {
            AudioPlayer(
                soundInstanceProvider = { streamId, stream ->
                    StaticSoundInstance(
                        stream,
                        streamId,
                        this.worldPosition,
                        SOUND_RADIUS,
                    )
                },
                playerId = recordPlayerUUID.toString()
            )
        }
    }

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

    val speedBasedPitchFunction = PitchFunction.smoothedRealTime(
        sourcePitchFunction = PitchFunction.custom { _ -> currentPitch },
        transitionTimeSeconds = PITCH_TRANSITION_TIME
    )

    fun startPlayer() {
        when {
            !hasRecord() -> {
                updatePlaybackState(PlaybackState.STOPPED, resetTime = true)
            }

            abs(this.speed) <= 0.0f -> {
                updatePlaybackState(PlaybackState.PAUSED, resetTime = false)
            }

            else -> {
                updatePlaybackState(PlaybackState.PLAYING, setCurrentTime = true)
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
        setCurrentTime: Boolean = false
    ) {
        if (playbackState == newState && !resetTime && !setCurrentTime) {
            return
        }

        val oldState = playbackState
        playbackState = newState

        when {
            resetTime -> playTime = 0
            setCurrentTime -> playTime = System.currentTimeMillis()
        }

        // Register/unregister player tracking
        when {
            newState == PlaybackState.PLAYING && oldState != PlaybackState.PLAYING -> {
                registerPlayer(recordPlayerUUID.toString(), this)
            }

            newState == PlaybackState.STOPPED && oldState != PlaybackState.STOPPED -> {
                unregisterPlayer(recordPlayerUUID.toString())
            }
        }

        notifyUpdate()
    }

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

    protected fun resumeClientPlayer() {
        audioPlayer.resume()
    }

    protected fun pauseClientPlayer() {
        audioPlayer.pause()
    }

    protected fun stopClientPlayer() {
        audioPlayer.stop()
    }

    override fun remove() {
        onClient {
            if (AudioPlayerRegistry.containsStream(recordPlayerUUID.toString())) {
                audioPlayer.dispose()
            }
        }
        unregisterPlayer(recordPlayerUUID.toString())
        super.remove()
    }

    override fun destroy() {
        stopPlayer()
        dropContent()
        super.destroy()
    }

    fun dropContent() {
        val currLevel = this.level ?: return
        val inv = SimpleContainer(inventoryHandler.slots)
        for (i in 0 until inventoryHandler.slots) {
            inv.setItem(i, inventoryHandler.getStackInSlot(i))
        }

        Containers.dropContents(currLevel, this.worldPosition, inv)
    }

    fun insertRecord(discItem: ItemStack, autoPlay: Boolean = false): Boolean {
        if (hasRecord()) return false
        inventoryHandler.insertItem(RECORD_SLOT, discItem.copy(), false)

        if (autoPlay && abs(this.speed) > 0.0f) {
            playbackState = PlaybackState.PLAYING
            notifyUpdate()
        }

        return true
    }

    fun popRecord(): ItemStack? {
        if (!hasRecord()) return null
        val item = inventoryHandler.extractItem(RECORD_SLOT, 1, false)
        return item
    }

    fun getRecord(): ItemStack {
        return inventoryHandler.getStackInSlot(RECORD_SLOT).copy()
    }

    fun getRecordItem(): EtherealRecordItem? {
        val recordStack = getRecord()
        if (recordStack.isEmpty || recordStack.item !is EtherealRecordItem) return null
        return recordStack.item as EtherealRecordItem
    }

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
        NBTHelper.writeEnum(compound, "PlaybackState", playbackState)
        compound.putUUID("RecordPlayerUUID", recordPlayerUUID)
        compound.putLong("PlayTime", playTime)
    }

    override fun read(compound: CompoundTag, clientPacket: Boolean) {
        super.read(compound, clientPacket)
        if (compound.contains("Inventory")) {
            inventoryHandler.deserializeNBT(compound.getCompound("Inventory"))
        }

        if (compound.contains("RecordPlayerUUID")) {
            recordPlayerUUID = compound.getUUID("RecordPlayerUUID")
        }

        if (compound.contains("PlayTime")) {
            playTime = compound.getLong("PlayTime")
        }

        if (compound.contains("PlaybackState")) {
            val newPlaybackState = NBTHelper.readEnum(compound, "PlaybackState", PlaybackState::class.java)
            val oldPlaybackState = playbackState

            onClient {
                if (oldPlaybackState != newPlaybackState) {
                    when {
                        newPlaybackState == PlaybackState.PLAYING &&
                                (oldPlaybackState == PlaybackState.PAUSED || oldPlaybackState == PlaybackState.MANUALLY_PAUSED) -> {
                            resumeClientPlayer()
                        }

                        newPlaybackState == PlaybackState.PLAYING && oldPlaybackState == PlaybackState.STOPPED -> {
                            val currentRecord = getRecord()
                            if (!currentRecord.isEmpty && currentRecord.item is EtherealRecordItem) {
                                val audioUrl = getAudioUrl(currentRecord)
                                if (!audioUrl.isNullOrEmpty()) {
                                    startClientPlayer(audioUrl)
                                }
                            }
                        }

                        newPlaybackState == PlaybackState.PAUSED || newPlaybackState == PlaybackState.MANUALLY_PAUSED -> {
                            pauseClientPlayer()
                        }

                        newPlaybackState == PlaybackState.STOPPED -> {
                            stopClientPlayer()
                        }
                    }
                }
            }
            playbackState = newPlaybackState
        }
    }


}