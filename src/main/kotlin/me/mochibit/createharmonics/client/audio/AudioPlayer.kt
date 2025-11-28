package me.mochibit.createharmonics.client.audio

import me.mochibit.createharmonics.CommonConfig
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.client.audio.effect.EffectChain
import me.mochibit.createharmonics.client.audio.process.FFmpegExecutor
import me.mochibit.createharmonics.client.audio.source.AudioSource
import me.mochibit.createharmonics.client.audio.source.HttpAudioSource
import me.mochibit.createharmonics.client.audio.source.YoutubeAudioSource
import me.mochibit.createharmonics.coroutine.withClientContext
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import java.io.InputStream
import java.util.*

typealias StreamId = String
typealias StreamingSoundInstanceProvider = (streamId: StreamId, stream: InputStream) -> SoundInstance

class AudioPlayer(
    val soundInstanceProvider: StreamingSoundInstanceProvider,
    val playerId: String = UUID.randomUUID().toString(),
    val sampleRate: Int = 48000
) {
    private val ffmpegExecutor = FFmpegExecutor()
    private var processingAudioStream: AudioEffectInputStream? = null
    private var currentSoundInstance: SoundInstance? = null

    private var offset = 0.0

    @Volatile
    private var paused = false

    val isPaused: Boolean get() = paused

    val isPlaying: Boolean get() = StreamRegistry.containsStream(playerId)


    suspend fun play(
        url: String,
        effectChain: EffectChain = EffectChain.empty(),
    ) {
        if (StreamRegistry.containsStream(playerId)) return
        val audioSource = resolveAudioSource(url) ?: return
        processingAudioStream = makeStream(audioSource, effectChain).also {
            currentSoundInstance = soundInstanceProvider(playerId, it)
        }

        withClientContext {
            currentSoundInstance?.let {
                Minecraft.getInstance().soundManager.play(it)
            }
        }
    }

    suspend fun stop() {
        withClientContext {
            currentSoundInstance?.let {
                Minecraft.getInstance().soundManager.stop(it)
            }
        }

        StreamRegistry.unregisterStream(playerId)
    }

    suspend fun pause() {
        paused = true
        withClientContext {
            currentSoundInstance?.let {
                Minecraft.getInstance().soundManager.stop(it)
            }
        }
    }

    suspend fun resume() {
        paused = false
        withClientContext {
            currentSoundInstance?.let {
                Minecraft.getInstance().soundManager.play(it)
            }
        }
    }

    private fun resolveAudioSource(url: String): AudioSource? {
        return when {
            url.contains("youtube.com") || url.contains("youtu.be") -> {
                YoutubeAudioSource(url)
            }

            url.startsWith("http://") || url.startsWith("https://") -> {
                val acceptedDomainList = CommonConfig.getAcceptedHttpDomains()
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

    private suspend fun makeStream(
        audioSource: AudioSource,
        effectChain: EffectChain,
    ): AudioEffectInputStream {
        val effectiveUrl = audioSource.resolveAudioUrl()
        ffmpegExecutor.createStream(effectiveUrl, sampleRate, offset)
        val stream = AudioEffectInputStream(
            ffmpegExecutor.inputStream!!,
            effectChain, sampleRate
        )
        StreamRegistry.registerStream(playerId, stream)
        return stream
    }
}