package me.mochibit.createharmonics.content.block.andesiteJukebox

import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.audio.AudioPlayer
import me.mochibit.createharmonics.audio.StreamRegistry
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.effect.LowPassFilterEffect
import me.mochibit.createharmonics.audio.effect.PitchShiftEffect
import me.mochibit.createharmonics.audio.effect.ReverbEffect
import me.mochibit.createharmonics.audio.effect.VolumeEffect
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
import kotlin.math.abs

class AndesiteJukeboxBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : KineticBlockEntity(type, pos, state) {

    private var isPlaying = false
    private var hasDisc = false // Internal disc state, separate from visual animation
    private var currentResourceLocation: ResourceLocation? = null
    private var playbackJob: Job? = null
    @Volatile
    private var currentPitch: Float = 1.0f // Thread-safe pitch value
    private val RICK_ASTLEY_URL = "https://www.youtube.com/watch?v=NLj6k85SEBk"
    private val MIN_SPEED_THRESHOLD = 16.0f

    val pitchFunction = PitchFunction.Companion.smoothedRealTime(
        sourcePitchFunction = PitchFunction.Companion.custom { time -> currentPitch },
        transitionTimeSeconds = 0.5
    )

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
        hasDisc = true
        // Disc inserted, will start playing when rotation is sufficient
        if (level?.isClientSide == true && abs(this.speed) >= MIN_SPEED_THRESHOLD) {
            startPlaying()
        }
    }

    fun ejectDisc() {
        hasDisc = false
        stopPlaying()
    }

    private fun hasDisc(): Boolean {
        return hasDisc
    }

    private fun startPlaying() {
        if (isPlaying || !level!!.isClientSide) return

        isPlaying = true
        Logger.info("AndesiteJukebox: Starting playback, initial pitch: $currentPitch")

        playbackJob = launchModCoroutine(Dispatchers.IO) {
            try {
                // Use real-time effects for dynamic pitch
                val resourceLocation = EtherealDiscItem.Companion.createResourceLocation(RICK_ASTLEY_URL, pitchFunction)
                currentResourceLocation = resourceLocation

                Logger.info("AndesiteJukebox: Resource location created: $resourceLocation")

                val stream = AudioPlayer.fromYoutube(
                    url = RICK_ASTLEY_URL,
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

                // Wait for pre-buffering to complete asynchronously (doesn't block game thread)
                Logger.info("AndesiteJukebox: Waiting for pre-buffering to complete...")
                val preBuffered = stream.awaitPreBuffering(timeoutSeconds = 30)

                if (!preBuffered) {
                    Logger.err("AndesiteJukebox: Pre-buffering timeout!")
                    isPlaying = false
                } else {
                    Logger.info("AndesiteJukebox: Pre-buffering complete, starting playback")

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