package me.mochibit.createharmonics.content.block.andesiteJukebox

import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.audio.AudioPlayer
import me.mochibit.createharmonics.audio.StreamRegistry
import me.mochibit.createharmonics.audio.effect.*
import me.mochibit.createharmonics.audio.instance.StaticSoundInstance
import me.mochibit.createharmonics.audio.pcm.PitchFunction
import me.mochibit.createharmonics.coroutine.launchModCoroutine
import me.mochibit.createharmonics.coroutine.withClientContext
import me.mochibit.createharmonics.registry.ModItemsRegistry
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.items.ItemStackHandler
import kotlin.math.abs

class AndesiteJukeboxBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : KineticBlockEntity(type, pos, state), MenuProvider {

    val inventory = object : ItemStackHandler(1) {
        override fun onContentsChanged(slot: Int) {
            setChanged()
            onInventoryChanged()
        }

        override fun isItemValid(slot: Int, stack: ItemStack): Boolean {
            return stack.item == ModItemsRegistry.etherealDisc.get()
        }
    }

    private var isPlaying = false
    private var currentResourceLocation: ResourceLocation? = null
    private var playbackJob: Job? = null

    @Volatile
    private var currentPitch: Float = 1.0f // Thread-safe pitch value
    private val MIN_SPEED_THRESHOLD = 16.0f

    val pitchFunction = PitchFunction.smoothedRealTime(
        sourcePitchFunction = PitchFunction.custom { time -> currentPitch },
        transitionTimeSeconds = 0.5
    )

    override fun tick() {
        super.tick()

        if (!level!!.isClientSide) return

        val speed = abs(this.speed)

        currentPitch = calculatePitchFromSpeed(speed)

        if (speed >= MIN_SPEED_THRESHOLD && !isPlaying && hasDisc()) {
            startPlaying()
        } else if (speed < MIN_SPEED_THRESHOLD && isPlaying) {
            stopPlaying()
        }
        // Real-time effects automatically read the updated currentPitch value
    }

    private fun hasDisc(): Boolean {
        return !inventory.getStackInSlot(0).isEmpty
    }

    private fun onInventoryChanged() {
        if (hasDisc() && level?.isClientSide == true && abs(this.speed) >= MIN_SPEED_THRESHOLD) {
            startPlaying()
        } else if (!hasDisc()) {
            stopPlaying()
        }
    }


    fun startPlaying() {
        if (isPlaying || !level!!.isClientSide) return

        isPlaying = true
        Logger.info("AndesiteJukebox: Starting playback, initial pitch: $currentPitch")

        playbackJob = launchModCoroutine(Dispatchers.IO) {
            try {
                val resourceLocation = generateResourceLocation("https://youtu.be/TkgDHsvxhlo?si=Qpat9rp9XbLsLx-2")
                currentResourceLocation = resourceLocation

                val stream = AudioPlayer.fromYoutube(
                    url = "https://youtu.be/TkgDHsvxhlo?si=Qpat9rp9XbLsLx-2",
                    effectChain = EffectChain(
                        listOf(
                            PitchShiftEffect(pitchFunction),
                            VolumeEffect(0.8f), // Reduce volume to 80%
                            LowPassFilterEffect(cutoffFrequency = 3000f), // Slight muffling
                            ReverbEffect(roomSize = 0.5f, damping = 0.2f, wetMix = 0.8f)
                        )
                    ),
                    resourceLocation = resourceLocation
                )


                val preBuffered = stream.awaitPreBuffering(timeoutSeconds = 30)

                if (!preBuffered) {
                    Logger.err("AndesiteJukebox: Pre-buffering timeout!")
                    isPlaying = false
                } else {
                    withClientContext {
                        Minecraft.getInstance().soundManager.play(
                            StaticSoundInstance(
                                resourceLocation = resourceLocation,
                                position = blockPos,
                                radius = 64,
                                pitch = 1.0f
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    isPlaying = false
                    Logger.err("AndesiteJukebox: Error starting playback: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    private fun restartPlaying() {
        stopPlaying()
        Thread.sleep(50) // Small delay
        startPlaying()
    }

    private fun calculatePitchFromSpeed(speed: Float): Float {
        val clampedSpeed = speed.coerceIn(16.0f, 256.0f)
        val pitch = 0.5f + (clampedSpeed - 16.0f) / (256.0f - 16.0f) * (2.0f - 0.5f)
        return pitch.coerceIn(0.5f, 2.0f)
    }

    private fun generateResourceLocation(url: String): ResourceLocation {
        val hash = url.hashCode().toString(16)
        return ResourceLocation.fromNamespaceAndPath(
            CreateHarmonicsMod.MOD_ID,
            "urlaudio_$hash"
        )
    }

    fun stopPlaying() {
        if (!isPlaying) return

        isPlaying = false
        playbackJob?.cancel()
        playbackJob = null

        currentResourceLocation?.let { location ->
            if (level?.isClientSide == true) {
                Minecraft.getInstance().soundManager.stop(location, null)
                StreamRegistry.unregisterStream(location)
            }
            currentResourceLocation = null
        }
    }

    override fun lazyTick() {
        super.lazyTick()
    }

    override fun destroy() {
        stopPlaying()
        super.destroy()
    }

    override fun write(compound: CompoundTag?, clientPacket: Boolean) {
        super.write(compound, clientPacket)
        compound?.put("inventory", inventory.serializeNBT())
    }

    override fun read(compound: CompoundTag?, clientPacket: Boolean) {
        super.read(compound, clientPacket)
        if (compound?.contains("inventory") == true) {
            inventory.deserializeNBT(compound.getCompound("inventory"))
        }
    }

    override fun createMenu(id: Int, playerInventory: Inventory, player: Player): AbstractContainerMenu {
        return AndesiteJukeboxMenu(id, playerInventory, this)
    }

    override fun getDisplayName(): Component {
        return Component.translatable("block.createharmonics.andesite_jukebox")
    }


}