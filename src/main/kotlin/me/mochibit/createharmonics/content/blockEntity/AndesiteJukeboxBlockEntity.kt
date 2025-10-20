package me.mochibit.createharmonics.content.blockEntity

import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.audio.AudioPlayer
import me.mochibit.createharmonics.audio.StreamRegistry
import me.mochibit.createharmonics.audio.instance.StaticSoundInstance
import me.mochibit.createharmonics.audio.pcm.PitchFunction
import me.mochibit.createharmonics.content.item.EtherealDiscItem
import me.mochibit.createharmonics.coroutine.launchModCoroutine
import me.mochibit.createharmonics.coroutine.withClientContext
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.effect.LowPassFilterEffect
import me.mochibit.createharmonics.audio.effect.PitchShiftEffect
import me.mochibit.createharmonics.audio.effect.ReverbEffect
import me.mochibit.createharmonics.audio.effect.VolumeEffect
import kotlin.math.abs

class AndesiteJukeboxBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : KineticBlockEntity(type, pos, state) {

    private var isPlaying = false
    private var currentResourceLocation: ResourceLocation? = null
    private var playbackJob: Job? = null
    @Volatile
    private var currentPitch: Float = 1.0f // Thread-safe pitch value
    private val RICK_ASTLEY_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    private val MIN_SPEED_THRESHOLD = 16.0f

    // Dynamic pitch function that reads the thread-safe currentPitch variable
    val pitchFunction = PitchFunction.custom { time ->
        currentPitch
    }

    override fun tick() {
        super.tick()

        if (!level!!.isClientSide) return

        val speed = abs(this.speed)

        // Store old pitch for comparison
        val oldPitch = currentPitch

        // Update the thread-safe pitch value every tick
        currentPitch = calculatePitchFromSpeed(speed)

        // Log pitch changes for debugging
        if (oldPitch != currentPitch && isPlaying) {
            Logger.info("AndesiteJukebox: Pitch updated from $oldPitch to $currentPitch (speed: $speed)")
        }

        if (speed >= MIN_SPEED_THRESHOLD && !isPlaying && hasDisc()) {
            startPlaying()
        } else if (speed < MIN_SPEED_THRESHOLD && isPlaying) {
            stopPlaying()
        }
        // Real-time effects automatically read the updated currentPitch value
    }

    fun insertDisc() {
        // Disc inserted, will start playing when rotation is sufficient
        if (level?.isClientSide == true && abs(this.speed) >= MIN_SPEED_THRESHOLD) {
            startPlaying()
        }
    }

    fun ejectDisc() {
        stopPlaying()
    }

    private fun hasDisc(): Boolean {
        return blockState.getValue(me.mochibit.createharmonics.content.block.AndesiteJukeboxBlock.HAS_DISC)
    }

    private fun startPlaying() {
        if (isPlaying || !level!!.isClientSide) return

        isPlaying = true
        Logger.info("AndesiteJukebox: Starting playback, initial pitch: $currentPitch")

        playbackJob = launchModCoroutine(Dispatchers.IO) {
            try {
                // Use real-time effects for dynamic pitch
                val resourceLocation = EtherealDiscItem.createResourceLocation(RICK_ASTLEY_URL, pitchFunction)
                currentResourceLocation = resourceLocation

                Logger.info("AndesiteJukebox: Resource location created: $resourceLocation")

                AudioPlayer.fromYoutube(
                    url = RICK_ASTLEY_URL,
                    effectChain = EffectChain(
                        listOf(
                            PitchShiftEffect(pitchFunction),
                            VolumeEffect(0.8f), // Reduce volume to 80%
                            LowPassFilterEffect(cutoffFrequency = 3000f), // Slight muffling
                            ReverbEffect(roomSize = 1.0f, 0.0f, wetMix = 1.0f),
                        )
                    ),
                    resourceLocation = resourceLocation
                )

                withClientContext {
                    val soundInstance = StaticSoundInstance(
                        resourceLocation = resourceLocation,
                        position = blockPos,
                        radius = 64,
                        pitch = 1.0f
                    )
                    Logger.info("AndesiteJukebox: Playing sound instance with real-time effects")
                    Minecraft.getInstance().soundManager.play(soundInstance)
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
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
}
