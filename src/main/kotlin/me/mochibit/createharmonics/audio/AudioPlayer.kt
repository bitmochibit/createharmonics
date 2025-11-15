package me.mochibit.createharmonics.audio

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import me.mochibit.createharmonics.Config
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.processor.AudioStreamProcessor
import me.mochibit.createharmonics.audio.source.AudioSource
import me.mochibit.createharmonics.audio.source.HttpAudioSource
import me.mochibit.createharmonics.audio.source.YoutubeAudioSource
import me.mochibit.createharmonics.coroutine.launchModCoroutine
import me.mochibit.createharmonics.coroutine.withClientContext
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.resources.ResourceLocation
import java.io.InputStream
import java.util.*


typealias StreamingSoundInstanceProvider = (stream: InputStream) -> SoundInstance
typealias StreamId = String

object AudioPlayer {
    private const val DEFAULT_SAMPLE_RATE = 48000

    fun createAudioStream(
        audioSource: AudioSource,
        effectChain: EffectChain = EffectChain.empty(),
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        streamId: String
    ): BufferedAudioStream {
        val processor = AudioStreamProcessor(sampleRate)
        val stream = BufferedAudioStream(audioSource, effectChain, sampleRate, processor)
        StreamRegistry.registerStream(streamId, stream)
        return stream
    }

    fun play(
        url: String,
        listenerId: String = UUID.randomUUID().toString(),
        soundInstanceProvider: StreamingSoundInstanceProvider,
        effectChain: EffectChain = EffectChain.empty(),
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
    ): StreamId? {
        if (StreamRegistry.containsStream(listenerId)) return null
        val audioSource = resolveAudioSource(url) ?: return null
        val stream = createAudioStream(audioSource, effectChain, sampleRate, listenerId)
        val soundInstance = soundInstanceProvider(stream)
        launchModCoroutine(Dispatchers.IO) {
            try {
                val preBuffered = stream.awaitPreBuffering(timeoutSeconds = 30)

                if (!preBuffered) {
                    Logger.err("Playback failed prebuffering")
                } else {
                    withClientContext {
                        Minecraft.getInstance().soundManager.play(soundInstance)
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Logger.err("Something went wrong with the record player, here's the problem")
                    e.printStackTrace()
                }
            }
        }

        return listenerId
    }

    /**
     * Returns an audio source for the given URL, or null if unsupported.
     * YouTube is supported directly, HTTP urls needs to point to direct audio files, and the domain must be whitelisted in config (client-side).
     * Returns null for unsupported URLs.
     */
    fun resolveAudioSource(url: String): AudioSource? {
        return when {
            url.contains("youtube.com") || url.contains("youtu.be") -> {
                YoutubeAudioSource(url)
            }
            url.startsWith("http://") || url.startsWith("https://") -> {
                val acceptedDomainList = Config.ACCEPTED_HTTP_DOMAINS.get()
                if (acceptedDomainList.any { domain -> url.contains(domain) }) {
                    HttpAudioSource(url)
                } else {
                    Logger.err("HTTP audio URL domain not in accepted list: $url")
                    null
                }
            }
            else -> {
                null
            }
        }
    }

    fun isPlaying(streamId: String): Boolean {
        return StreamRegistry.containsStream(streamId)
    }

    fun stopStream(streamId: String) {
        launchModCoroutine {
            Minecraft.getInstance().soundManager.stop(streamId.toStreamResLocation(), null)
            StreamRegistry.unregisterStream(streamId)
        }
    }

    private fun String.toStreamResLocation(): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(
            CreateHarmonicsMod.MOD_ID,
            this
        )
    }
}
