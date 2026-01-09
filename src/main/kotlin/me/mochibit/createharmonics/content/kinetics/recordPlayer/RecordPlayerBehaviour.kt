package me.mochibit.createharmonics.content.kinetics.recordPlayer

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
import me.mochibit.createharmonics.event.contraption.ContraptionDisassembleEvent
import me.mochibit.createharmonics.extension.onClient
import me.mochibit.createharmonics.extension.onServer
import me.mochibit.createharmonics.extension.remapTo
import me.mochibit.createharmonics.network.packet.AudioPlayerContextStopPacket
import me.mochibit.createharmonics.registry.ModConfigurations
import me.mochibit.createharmonics.registry.ModPackets
import net.createmod.catnip.nbt.NBTHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.particle.NoteParticle
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.ShriekParticleOption
import net.minecraft.nbt.CompoundTag
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.Containers
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.RecordItem
import net.minecraft.world.item.context.BlockPlaceContext
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
        be.onServer {
            unregisterPlayer(recordPlayerUUID.toString())
        }
        lazyItemHandler.invalidate()
        super.unload()
    }

    override fun destroy() {
        stopPlayer()
        dropContent()
        unregisterPlayer(recordPlayerUUID.toString())
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
                    when (newPlaybackState) {
                        PlaybackState.PLAYING if (
                            oldPlaybackState == PlaybackState.PAUSED ||
                                oldPlaybackState == PlaybackState.MANUALLY_PAUSED
                        ) -> {
                            resumeClientPlayer()
                        }

                        PlaybackState.PLAYING if oldPlaybackState == PlaybackState.STOPPED -> {
                            val currentRecord = getRecord()
                            if (!currentRecord.isEmpty && currentRecord.item is EtherealRecordItem) {
                                startClientPlayer(currentRecord)
                            }
                        }

                        PlaybackState.PAUSED, PlaybackState.MANUALLY_PAUSED -> {
                            pauseClientPlayer()
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
        this.stopPlayer()
    }

    fun onAudioTitleUpdate(audioTitle: String) {
        if (audioTitle == "Unknown") return
        audioPlayingTitle = audioTitle
    }
}
