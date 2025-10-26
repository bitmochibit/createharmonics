package me.mochibit.createharmonics.content.block.recordPlayer

import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.audio.AudioPlayer
import me.mochibit.createharmonics.audio.StreamRegistry
import me.mochibit.createharmonics.audio.effect.*
import me.mochibit.createharmonics.audio.instance.StaticSoundInstance
import me.mochibit.createharmonics.audio.pcm.PitchFunction
import me.mochibit.createharmonics.content.item.EtherealDiscItem
import me.mochibit.createharmonics.coroutine.launchModCoroutine
import me.mochibit.createharmonics.coroutine.withClientContext
import me.mochibit.createharmonics.extension.onClient
import me.mochibit.createharmonics.network.ModNetworkHandler
import me.mochibit.createharmonics.network.RemoveModAudioPlayerPacket
import me.mochibit.createharmonics.registry.ModItemsRegistry
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.items.IItemHandler
import net.minecraftforge.items.ItemStackHandler
import java.util.UUID
import kotlin.concurrent.Volatile
import kotlin.math.abs

fun Float.remapTo(inMin: Float, inMax: Float, outMin: Float, outMax: Float): Float {
    return outMin + (this - inMin) / (inMax - inMin) * (outMax - outMin)
}

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
            return stack.item == ModItemsRegistry.etherealDisc.get()
        }
    }
    protected val lazyInventoryHandler: LazyOptional<IItemHandler> = LazyOptional.of { inventoryHandler }

    val speedBasedPitchFunction = PitchFunction.smoothedRealTime(
        sourcePitchFunction = PitchFunction.custom { _ -> currentPitch },
        transitionTimeSeconds = 0.5
    )

    fun calculatePitch(): Float {
        val currSpeed = abs(this.speed)
        if (currSpeed == storedSpeed) return currentPitch
        return currSpeed.remapTo(16.0f, 256.0f, MIN_PITCH, MAX_PITCH)
    }

    fun startPlayer() {
        if (!hasDisc()) return

        if (playbackState == PlaybackState.PLAYING) {
            pausePlayer()
            return
        }


        playbackState = PlaybackState.PLAYING
        audioResourceLocation = AudioPlayer.generateResourceLocation("https://www.youtube.com/watch?v=ZlHRhzXezAc", playerUUID.toString())
        onClient {
            this.startClientPlayer()
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

    protected fun startClientPlayer() {
        audioResourceLocation?.let { resLoc ->
            AudioPlayer.fromYoutube(
                blockPos,
                url = "https://www.youtube.com/watch?v=ZlHRhzXezAc",
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

    override fun destroy() {
        audioResourceLocation?.let { resLoc ->
            ModNetworkHandler.sendToAll(
                RemoveModAudioPlayerPacket(resLoc)
            )
        }
        super.destroy()
        // Drops
    }


    fun insertDisc(discItem: EtherealDiscItem): Boolean {
        if (hasDisc()) return false
        inventoryHandler.insertItem(RECORD_SLOT, ItemStack(discItem), false)
        return true
    }

    fun popDisc(): EtherealDiscItem? {
        if (!hasDisc()) return null
        val item = inventoryHandler.extractItem(RECORD_SLOT, 1, false)
        return item.item as? EtherealDiscItem
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
        if (!level!!.isClientSide) return
        currentPitch = calculatePitch()
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

            // If we're on client side and should be playing, start playback
            if (level?.isClientSide == true && playbackState == PlaybackState.PLAYING && hasDisc()) {
                startClientPlayer()
            }
        }
    }
}