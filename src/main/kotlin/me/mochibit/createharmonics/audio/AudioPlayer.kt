package me.mochibit.createharmonics.audio

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
     * Stream audio from any AudioSource with real-time pitch shifting.
     */
    fun streamAudio(
        audioSource: AudioSource,
        pitchFunction: PitchFunction = PitchFunction.constant(1.0f),
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        resourceLocation: ResourceLocation
    ): InputStream {
        val processor = AudioStreamProcessor(sampleRate)
        val stream = BufferedAudioStream(audioSource, pitchFunction, sampleRate, resourceLocation, processor)
        StreamRegistry.registerStream(resourceLocation, stream)
        return stream
    }

    /**
     * Stream audio from a YouTube URL (convenience method).
     */
    fun fromYoutube(
        url: String,
        pitchFunction: PitchFunction = PitchFunction.constant(1.0f),
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        resourceLocation: ResourceLocation
    ): InputStream {
        return streamAudio(YoutubeAudioSource(url), pitchFunction, sampleRate, resourceLocation)
    }

    /**
     * Stream audio from a local file (convenience method).
     */
    fun fromFile(
        filePath: String,
        pitchFunction: PitchFunction = PitchFunction.constant(1.0f),
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        resourceLocation: ResourceLocation
    ): InputStream {
        return streamAudio(LocalFileAudioSource(filePath), pitchFunction, sampleRate, resourceLocation)
    }

    /**
     * Stream audio from an HTTP URL (convenience method).
     */
    fun fromHttp(
        url: String,
        pitchFunction: PitchFunction = PitchFunction.constant(1.0f),
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        resourceLocation: ResourceLocation
    ): InputStream {
        return streamAudio(HttpAudioSource(url), pitchFunction, sampleRate, resourceLocation)
    }

    /**
     * Shutdown and cleanup.
     */
    fun shutdown() {
        StreamRegistry.clear()
        AudioUrlCache.clear()
    }
}
