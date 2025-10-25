package me.mochibit.createharmonics.audio

import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.effect.PitchShiftEffect
import me.mochibit.createharmonics.audio.pcm.PitchFunction
import me.mochibit.createharmonics.audio.processor.AudioStreamProcessor
import me.mochibit.createharmonics.audio.source.AudioSource
import me.mochibit.createharmonics.audio.source.HttpAudioSource
import me.mochibit.createharmonics.audio.source.LocalFileAudioSource
import me.mochibit.createharmonics.audio.source.YoutubeAudioSource
import net.minecraft.resources.ResourceLocation
import java.io.InputStream

/**
 * Factory for creating audio streams from various sources.
 * Provides a unified API for working with different audio sources.
 */
object AudioPlayer {
    private const val DEFAULT_SAMPLE_RATE = 48000

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
    suspend fun fromYoutube(
        url: String,
        effectChain: EffectChain,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        resourceLocation: ResourceLocation
    ): BufferedAudioStream {
        val audioSource = YoutubeAudioSource(url)
        val processor = AudioStreamProcessor(sampleRate)
        val stream = BufferedAudioStream(audioSource, effectChain, sampleRate, resourceLocation, processor)
        StreamRegistry.registerStream(resourceLocation, stream)
        return stream
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
}
