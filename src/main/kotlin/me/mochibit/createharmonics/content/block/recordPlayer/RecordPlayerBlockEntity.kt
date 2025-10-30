package me.mochibit.createharmonics.content.block.recordPlayer

import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import me.mochibit.createharmonics.audio.AudioPlayer
import me.mochibit.createharmonics.audio.effect.*
import me.mochibit.createharmonics.audio.pcm.PitchFunction
import me.mochibit.createharmonics.content.item.EtherealDiscItem
import me.mochibit.createharmonics.event.recordPlayer.RecordPlayerPlayEvent
import me.mochibit.createharmonics.extension.onClient
import me.mochibit.createharmonics.extension.remapTo
import me.mochibit.createharmonics.registry.ModItemsRegistry
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.Containers
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.common.MinecraftForge
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
    var playerUUID: UUID = UUID.randomUUID()
) : KineticBlockEntity(type, pos, state) {
    private var storedSpeed: Float = .0f

    @Volatile
    protected var currentPitch: Float = MIN_PITCH

    @Volatile
    var playbackState: PlaybackState = PlaybackState.STOPPED

    @Volatile
    var audioResourceLocation: ResourceLocation? = null

    companion object {
        const val RECORD_SLOT = 0
        const val MIN_PITCH = 0.5f
        const val MAX_PITCH = 2.0f
    }

    val inventoryHandler = object : ItemStackHandler(1) {
        override fun isItemValid(slot: Int, stack: ItemStack): Boolean {
            return stack.item is EtherealDiscItem
        }
    }
    protected val lazyInventoryHandler: LazyOptional<IItemHandler> = LazyOptional.of { inventoryHandler }

    val speedBasedPitchFunction = PitchFunction.smoothedRealTime(
        sourcePitchFunction = PitchFunction.custom { _ -> currentPitch },
        transitionTimeSeconds = 0.5
    )

    private fun calculatePitch(): Float {
        val currSpeed = abs(this.speed)
        if (currSpeed == storedSpeed) return currentPitch
        return currSpeed.remapTo(16.0f, 256.0f, MIN_PITCH, MAX_PITCH)
    }

    fun startPlayer() {
        val currentDisc = getDisc()
        if (currentDisc.isEmpty || currentDisc.item !is EtherealDiscItem) return

        if (playbackState == PlaybackState.PLAYING) {
            pausePlayer()
            return
        }

        val audioUrl = EtherealDiscItem.getAudioUrl(currentDisc)
        if (audioUrl == null || audioUrl.isEmpty()) return


        playbackState = PlaybackState.PLAYING
        audioResourceLocation =
            AudioPlayer.generateResourceLocation(audioUrl, playerUUID.toString())
                .let {
                    MinecraftForge.EVENT_BUS.post(
                        RecordPlayerPlayEvent(this, it)
                    )
                    it
                }


        onClient {
            this.startClientPlayer(audioUrl)
        }
    }

    fun stopPlayer() {
        playbackState = PlaybackState.STOPPED
        onClient {
            this.stopClientPlayer()
        }
    }

    fun pausePlayer() {
        playbackState = PlaybackState.PAUSED
        onClient {
            this.pauseClientPlayer()
        }
    }

    protected fun startClientPlayer(audioUrl: String) {
        audioResourceLocation?.let { resLoc ->
            AudioPlayer.fromYoutube(
                blockPos,
                url = audioUrl,
                effectChain = EffectChain(
                    listOf(
                        PitchShiftEffect(speedBasedPitchFunction),
                        VolumeEffect(0.8f), // Reduce volume to 80%
                        LowPassFilterEffect(cutoffFrequency = 3000f), // Slight muffling
                        ReverbEffect(roomSize = 0.5f, damping = 0.2f, wetMix = 0.8f)
                    )
                ),
                resourceLocation = resLoc
            )
        }
    }

    protected fun pauseClientPlayer() {
        audioResourceLocation?.let { resLoc ->
            AudioPlayer.stopStream(resLoc)
        }
    }

    protected fun stopClientPlayer() {
        audioResourceLocation?.let { resLoc ->
            AudioPlayer.stopStream(resLoc)
        }
    }

    /**
     * Both client and server side handling
     */
    override fun remove() {
        onClient {
            stopClientPlayer()
        }
        super.remove()
    }

    /**
     * Pure server side handling
     */
    override fun destroy() {
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

    fun insertDisc(discItem: ItemStack): Boolean {
        if (hasDisc()) return false
        inventoryHandler.insertItem(RECORD_SLOT, discItem.copy(), false)
        return true
    }

    fun popDisc(): ItemStack? {
        if (!hasDisc()) return null
        val item = inventoryHandler.extractItem(RECORD_SLOT, 1, false)
        return item
    }

    fun getDisc(): ItemStack {
        return inventoryHandler.getStackInSlot(RECORD_SLOT).copy()
    }

    fun hasDisc(): Boolean {
        return !inventoryHandler.getStackInSlot(RECORD_SLOT).isEmpty
    }

    override fun <T : Any?> getCapability(cap: Capability<T?>): LazyOptional<T?> {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return lazyInventoryHandler.cast()
        }
        return super.getCapability(cap)
    }

    override fun tick() {
        super.tick()
        currentPitch = calculatePitch()

        when {
            abs(this.speed) > .0f && playbackState == PlaybackState.PAUSED -> startPlayer()
            abs(this.speed) == .0f && playbackState == PlaybackState.PLAYING -> pausePlayer()
        }
    }

    override fun write(compound: CompoundTag?, clientPacket: Boolean) {
        super.write(compound, clientPacket)
        compound?.put("inventory", inventoryHandler.serializeNBT())
        compound?.putInt("playbackState", playbackState.ordinal)
        compound?.putString("playerUUID", playerUUID.toString())
    }

    override fun read(compound: CompoundTag?, clientPacket: Boolean) {
        super.read(compound, clientPacket)
        if (compound?.contains("inventory") == true) {
            inventoryHandler.deserializeNBT(compound.getCompound("inventory"))
        }

        if (compound?.contains("playerUUID") == true) {
            val uuidString = compound.getString("playerUUID")
            playerUUID = UUID.fromString(uuidString)
        }

        if (compound?.contains("playbackState") == true) {
            playbackState = PlaybackState.fromOrdinal(compound.getInt("playbackState"))
            level?.onClient {
                val currentDisc = getDisc()
                val audioUrl = EtherealDiscItem.getAudioUrl(currentDisc)
                if (playbackState == PlaybackState.PLAYING && !audioUrl.isNullOrEmpty()) {
                    startClientPlayer(audioUrl)
                }
            }
        }
    }
}