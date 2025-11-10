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
import java.util.UUID


typealias SoundInstanceProvider = (ResourceLocation) -> SoundInstance

object AudioPlayer {
    private const val DEFAULT_SAMPLE_RATE = 48000

    /**
     * Stream audio from any AudioSource with an effect chain.
     */
    fun createAudioStream(
        audioSource: AudioSource,
        effectChain: EffectChain = EffectChain.empty(),
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        streamResLoc: ResourceLocation
    ): BufferedAudioStream {
        val processor = AudioStreamProcessor(sampleRate)
        val stream = BufferedAudioStream(audioSource, effectChain, sampleRate, processor)
        StreamRegistry.registerStream(streamResLoc, stream)
        return stream
    }

    fun play(
        url: String,
        soundInstanceProvider: SoundInstanceProvider,
        effectChain: EffectChain = EffectChain.empty(),
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        streamId: String = UUID.randomUUID().toString()
    ) {
        val audioSource = resolveAudioSource(url) ?: return
        val resourceLoc = generateResourceLocation(streamId)
        if (StreamRegistry.containsStream(resourceLoc)) return
        val stream = createAudioStream(audioSource, effectChain, sampleRate, resourceLoc)
        val soundInstance = soundInstanceProvider(resourceLoc)
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
        val resLoc = generateResourceLocation(streamId)
        return StreamRegistry.containsStream(resLoc)
    }

    fun stopStream(streamId: String) {
        launchModCoroutine {
            val resLoc = generateResourceLocation(streamId)
            Minecraft.getInstance().soundManager.stop(resLoc, null)
            StreamRegistry.unregisterStream(resLoc)
        }
    }

    fun generateResourceLocation(streamId: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(
            CreateHarmonicsMod.MOD_ID,
            "harmonics-audio-$streamId"
        )
    }
}
