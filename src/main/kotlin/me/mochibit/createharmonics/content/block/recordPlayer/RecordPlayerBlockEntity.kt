package me.mochibit.createharmonics.content.block.recordPlayer

import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import me.mochibit.createharmonics.audio.AudioPlayer
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

open class RecordPlayerBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState,
) : KineticBlockEntity(type, pos, state) {

    enum class PlaybackState {
        PLAYING,
        STOPPED,
        PAUSED;
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

    companion object {
        const val RECORD_SLOT = 0
        const val MIN_PITCH = 0.5f
        const val MAX_PITCH = 2.0f
    }

    val inventoryHandler = object : ItemStackHandler(1) {
        override fun isItemValid(slot: Int, stack: ItemStack): Boolean {
            return stack.item is EtherealRecordItem
        }
    }
    protected val lazyInventoryHandler: LazyOptional<IItemHandler> = LazyOptional.of { inventoryHandler }

    val speedBasedPitchFunction = PitchFunction.smoothedRealTime(
        sourcePitchFunction = PitchFunction.custom { _ -> currentPitch },
        transitionTimeSeconds = 0.5
    )

    fun startPlayer() {
        // Only proceed if we have a record
        if (!hasRecord()) {
            if (playbackState != PlaybackState.STOPPED) {
                playbackState = PlaybackState.STOPPED
                notifyUpdate()
            }
            return
        }

        // Only proceed if speed is sufficient
        if (abs(this.speed) <= 0.0f) {
            if (playbackState != PlaybackState.PAUSED) {
                playbackState = PlaybackState.PAUSED
                notifyUpdate()
            }
            return
        }

        // Only update if state actually changed
        if (playbackState != PlaybackState.PLAYING) {
            playbackState = PlaybackState.PLAYING
            notifyUpdate()
        }
    }

    fun stopPlayer() {
        if (playbackState != PlaybackState.STOPPED) {
            playbackState = PlaybackState.STOPPED
            notifyUpdate()
        }
    }

    fun pausePlayer() {
        if (playbackState != PlaybackState.PAUSED) {
            playbackState = PlaybackState.PAUSED
            notifyUpdate()
        }
    }

    protected fun startClientPlayer(audioUrl: String) {
        AudioPlayer.play(
            audioUrl,
            listenerId = playerUUID.toString(),
            soundInstanceProvider = { resLoc ->
                StaticSoundInstance(
                    resLoc,
                    this.worldPosition,
                    64
                )
            },
            EffectChain(
                listOf(
                    PitchShiftEffect(speedBasedPitchFunction),
                    VolumeEffect(0.8f), // Reduce volume to 80%
                    LowPassFilterEffect(cutoffFrequency = 3000f), // Slight muffling
                    ReverbEffect(roomSize = 0.5f, damping = 0.2f, wetMix = 0.8f)
                )
            ),
        )
    }

    protected fun pauseClientPlayer() {
        AudioPlayer.stopStream(playerUUID.toString())
    }

    protected fun stopClientPlayer() {
        AudioPlayer.stopStream(playerUUID.toString())
    }

    override fun remove() {
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

    fun insertRecord(discItem: ItemStack): Boolean {
        if (hasRecord()) return false
        inventoryHandler.insertItem(RECORD_SLOT, discItem.copy(), false)
        notifyUpdate()
        return true
    }

    fun popRecord(): ItemStack? {
        if (!hasRecord()) return null
        val item = inventoryHandler.extractItem(RECORD_SLOT, 1, false)
        notifyUpdate()
        return item
    }

    fun getRecord(): ItemStack {
        return inventoryHandler.getStackInSlot(RECORD_SLOT).copy()
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

            val desiredState = when {
                !hasDisc -> PlaybackState.STOPPED
                currentSpeed == 0.0f -> PlaybackState.PAUSED
                currentSpeed > 0.0f -> PlaybackState.PLAYING
                else -> PlaybackState.STOPPED
            }

            if (playbackState != desiredState) {
                playbackState = desiredState
                notifyUpdate()
            }
        }
    }

    override fun write(compound: CompoundTag, clientPacket: Boolean) {
        super.write(compound, clientPacket)
        compound.put("Inventory", inventoryHandler.serializeNBT())
        NBTHelper.writeEnum(compound, "playbackState", playbackState)
        compound.putUUID("playerUUID", playerUUID)
    }

    override fun read(compound: CompoundTag, clientPacket: Boolean) {
        super.read(compound, clientPacket)
        if (compound.contains("Inventory")) {
            inventoryHandler.deserializeNBT(compound.getCompound("Inventory"))
        }

        if (compound.contains("playerUUID")) {
            playerUUID = compound.getUUID("playerUUID")
        }

        if (compound.contains("playbackState")) {
            val newPlaybackState = NBTHelper.readEnum(compound, "playbackState", PlaybackState::class.java)
            onClient {
                when (newPlaybackState) {
                    PlaybackState.PLAYING -> {
                        val currentRecord = getRecord()
                        if (!currentRecord.isEmpty && currentRecord.item is EtherealRecordItem) {
                            val audioUrl = currentRecord.getAudioUrl()
                            if (!audioUrl.isNullOrEmpty()) {
                                startClientPlayer(audioUrl)
                            }
                        }
                    }
                    PlaybackState.PAUSED -> {
                        pauseClientPlayer()
                    }
                    PlaybackState.STOPPED -> {
                        stopClientPlayer()
                    }
                }
            }
        }
    }


}