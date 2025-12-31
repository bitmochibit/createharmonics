package me.mochibit.createharmonics.content.kinetics.recordPlayer

import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import me.mochibit.createharmonics.audio.AudioPlayer
import me.mochibit.createharmonics.audio.AudioPlayerRegistry
import me.mochibit.createharmonics.audio.comp.PitchSupplierInterpolated
import me.mochibit.createharmonics.audio.comp.SoundEventComposition
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.effect.getStreamDirectly
import me.mochibit.createharmonics.audio.instance.SimpleStreamSoundInstance
import me.mochibit.createharmonics.audio.stream.Ogg2PcmInputStream
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerItemHandler.Companion.MAIN_RECORD_SLOT
import me.mochibit.createharmonics.content.records.EtherealRecordItem
import me.mochibit.createharmonics.content.records.EtherealRecordItem.Companion.getAudioUrl
import me.mochibit.createharmonics.content.records.EtherealRecordItem.Companion.playFromRecord
import me.mochibit.createharmonics.content.records.RecordCraftingHandler
import me.mochibit.createharmonics.extension.onClient
import me.mochibit.createharmonics.extension.onServer
import me.mochibit.createharmonics.extension.remapTo
import me.mochibit.createharmonics.registry.ModConfigurations
import net.createmod.catnip.nbt.NBTHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.Containers
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.RecordItem
import net.minecraftforge.common.util.LazyOptional
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
        }
    }

    private val maxPitch =
        ModConfigurations.common.maxPitch
            .get()
            .toFloat()
    private val minPitch =
        ModConfigurations.common.minPitch
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
    var soundRadius: Int = 16

    @Volatile
    var playbackState: PlaybackState = PlaybackState.STOPPED
        private set

    @Volatile
    var playTime: Long = 0
        private set

    @Volatile
    var audioPlayCount: Long = 0
        private set

    @Volatile
    var audioPlayingTitle: String? = null
        private set

    val itemHandler = RecordPlayerItemHandler(this, 1)
    val lazyItemHandler: LazyOptional<RecordPlayerItemHandler> = LazyOptional.of { itemHandler }

    val pitchSupplierInterpolated = PitchSupplierInterpolated({ currentPitch }, 500)

    private val audioPlayer: AudioPlayer by lazy {
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
    }

    override fun getType(): BehaviourType<RecordPlayerBehaviour> = BEHAVIOUR_TYPE

    override fun tick() {
        super.tick()

        val level = be.level ?: return
        level.onServer {
            val currentSpeed = abs(be.speed)
            val hasDisc = hasRecord()
            val isPowered = level.hasNeighborSignal(be.blockPos)

            when {
                !hasDisc -> {
                    if (playbackState != PlaybackState.STOPPED) {
                        updatePlaybackState(PlaybackState.STOPPED, resetTime = true)
                        audioPlayCount = 0
                        audioPlayingTitle = null
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
                            audioPlayCount += 1
                            handleRecordUse()
                        }
                    } else {
                        if (playbackState == PlaybackState.PAUSED) {
                            updatePlaybackState(PlaybackState.PLAYING, setCurrentTime = true)
                            handleRecordUse()
                        }
                    }
                }
            }
        }
    }

    fun handleRecordUse() {
        be.level?.onServer { level ->
            val damaged = getRecord()
            val broken = damaged.hurt(1, RandomSource.create(), null)

            if (broken) {
                setRecord(ItemStack.EMPTY)

                val inv = SimpleContainer(itemHandler.slots)
                for (i in 0 until itemHandler.slots) {
                    inv.setItem(i, ItemStack(Items.AMETHYST_SHARD))
                }

                Containers.dropContents(level, be.blockPos, inv)
                level.playSound(null, be.blockPos, SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f)
            } else {
                setRecord(damaged)
            }
        }
    }

    fun hasRecord(): Boolean = !itemHandler.getStackInSlot(MAIN_RECORD_SLOT).isEmpty

    fun insertRecord(discItem: ItemStack): Boolean {
        if (hasRecord()) return false
        itemHandler.insertItem(MAIN_RECORD_SLOT, discItem.copy(), false)
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
            resetTime -> playTime = 0
            setCurrentTime -> playTime = System.currentTimeMillis()
        }

        when {
            newState == PlaybackState.PLAYING && oldState != PlaybackState.PLAYING -> {
                registerPlayer(recordPlayerUUID.toString(), be)
            }

            newState == PlaybackState.STOPPED && oldState != PlaybackState.STOPPED -> {
                unregisterPlayer(recordPlayerUUID.toString())
            }
        }

        be.notifyUpdate()
    }

    private fun startClientPlayer(currentRecord: ItemStack) {
        val offsetSeconds =
            if (playTime > 0) {
                (System.currentTimeMillis() - this.playTime) / 1000.0
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
        be.level?.onClient { level, virtual ->
            if (AudioPlayerRegistry.containsStream(recordPlayerUUID.toString())) {
                audioPlayer.dispose()
            }
        }
        unregisterPlayer(recordPlayerUUID.toString())
        lazyItemHandler.invalidate()
    }

    override fun destroy() {
        super.destroy()
        stopPlayer()
        dropContent()
    }

    override fun write(
        compound: CompoundTag,
        clientPacket: Boolean,
    ) {
        compound.put("Inventory", itemHandler.serializeNBT())
        NBTHelper.writeEnum(compound, "PlaybackState", playbackState)
        compound.putUUID("RecordPlayerUUID", recordPlayerUUID)
        compound.putLong("PlayTime", playTime)
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
                    when {
                        newPlaybackState == PlaybackState.PLAYING &&
                            (oldPlaybackState == PlaybackState.PAUSED || oldPlaybackState == PlaybackState.MANUALLY_PAUSED) -> {
                            resumeClientPlayer()
                        }

                        newPlaybackState == PlaybackState.PLAYING && oldPlaybackState == PlaybackState.STOPPED -> {
                            val currentRecord = getRecord()
                            if (!currentRecord.isEmpty && currentRecord.item is EtherealRecordItem) {
                                startClientPlayer(currentRecord)
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

    fun onPlaybackEnd(endedPlayerId: String) {
        this.stopPlayer()
    }

    fun onAudioTitleUpdate(audioTitle: String) {
        if (audioTitle == "Unknown") return
        audioPlayingTitle = audioTitle
    }
}
