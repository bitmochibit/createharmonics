package me.mochibit.createharmonics.audio

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.effect.LowPassFilterEffect
import me.mochibit.createharmonics.audio.effect.PitchShiftEffect
import me.mochibit.createharmonics.audio.effect.ReverbEffect
import me.mochibit.createharmonics.audio.effect.VolumeEffect
import me.mochibit.createharmonics.audio.instance.StaticSoundInstance
import me.mochibit.createharmonics.audio.pcm.PitchFunction
import me.mochibit.createharmonics.audio.processor.AudioStreamProcessor
import me.mochibit.createharmonics.audio.source.AudioSource
import me.mochibit.createharmonics.audio.source.HttpAudioSource
import me.mochibit.createharmonics.audio.source.LocalFileAudioSource
import me.mochibit.createharmonics.audio.source.YoutubeAudioSource
import me.mochibit.createharmonics.coroutine.launchModCoroutine
import me.mochibit.createharmonics.coroutine.withClientContext
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import java.io.InputStream


object AudioPlayer {
    private const val DEFAULT_SAMPLE_RATE = 48000

    val runningStreams : HashMap<ResourceLocation, Job> = HashMap()


    /**
     * Stream audio from any AudioSource with an effect chain.
     */
    fun streamAudio(
        audioSource: AudioSource,
        effectChain: EffectChain = EffectChain.empty(),
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        resourceLocation: ResourceLocation
    ): InputStream {
        val processor = AudioStreamProcessor(sampleRate)
        val stream = BufferedAudioStream(audioSource, effectChain, sampleRate, resourceLocation, processor)
        StreamRegistry.registerStream(resourceLocation, stream)
        return stream
    }

    /**
     * Stream audio from a YouTube URL with effect chain.
     * Returns the BufferedAudioStream so caller can await pre-buffering before playing.
     */
    fun fromYoutube(
        blockPos: BlockPos,
        url: String,
        effectChain: EffectChain,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        resourceLocation: ResourceLocation = generateResourceLocation(url)
    ): ResourceLocation {
        if (runningStreams.containsKey(resourceLocation)) {
            return resourceLocation
        }
        val audioSource = YoutubeAudioSource(url)
        val processor = AudioStreamProcessor(sampleRate)
        val stream = BufferedAudioStream(audioSource, effectChain, sampleRate, resourceLocation, processor)
        StreamRegistry.registerStream(resourceLocation, stream)

        val playbackJob = launchModCoroutine(Dispatchers.IO) {
            try {
                val preBuffered = stream.awaitPreBuffering(timeoutSeconds = 30)

                if (!preBuffered) {
                    Logger.err("Playback failed prebuffering")
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
                    Logger.err("Something went wrong with the record player, here's the problem")
                    e.printStackTrace()
                }
            }
        }

        runningStreams[resourceLocation] = playbackJob
        return resourceLocation
    }

    fun stopStream(resourceLocation: ResourceLocation) {
        launchModCoroutine {
            val job = runningStreams[resourceLocation]
            job?.cancel()
            Minecraft.getInstance().soundManager.stop(resourceLocation, null)
            runningStreams.remove(resourceLocation)
            StreamRegistry.unregisterStream(resourceLocation)
        }
    }

    /**
     * Stream audio from a local file with effect chain.
     */
    fun fromFile(
        filePath: String,
        effectChain: EffectChain,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        resourceLocation: ResourceLocation
    ): InputStream {
        return streamAudio(LocalFileAudioSource(filePath), effectChain, sampleRate, resourceLocation)
    }

    /**
     * Stream audio from an HTTP URL with effect chain.
     */
    fun fromHttp(
        url: String,
        effectChain: EffectChain,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        resourceLocation: ResourceLocation
    ): InputStream {
        return streamAudio(HttpAudioSource(url), effectChain, sampleRate, resourceLocation)
    }

    /**
     * Shutdown and cleanup.
     */
    fun shutdown() {
        StreamRegistry.clear()
        AudioUrlCache.clear()
    }

    fun generateResourceLocation(urlOrPath: String, randomizer: String = ""): ResourceLocation {
        val hash = "$urlOrPath$randomizer".hashCode().toString(16)
        return ResourceLocation.fromNamespaceAndPath(
            CreateHarmonicsMod.MOD_ID,
            "harmonics_audio_$hash"
        )
    }
}
