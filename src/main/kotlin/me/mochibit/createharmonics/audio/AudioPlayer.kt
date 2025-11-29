package me.mochibit.createharmonics.audio

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.mochibit.createharmonics.CommonConfig
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.process.FFmpegExecutor
import me.mochibit.createharmonics.audio.source.AudioSource
import me.mochibit.createharmonics.audio.source.HttpAudioSource
import me.mochibit.createharmonics.audio.source.YoutubeAudioSource
import me.mochibit.createharmonics.coroutine.launchModCoroutine
import me.mochibit.createharmonics.coroutine.withClientContext
import me.mochibit.createharmonics.network.ModNetworkHandler
import me.mochibit.createharmonics.network.packet.RequestPlayerLobbyPacket
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
    enum class PlayState {
        STOPPED,
        PLAYING,
        PAUSED,
    }

    private val stateMutex = Mutex()
    private val ffmpegExecutor = FFmpegExecutor()
    private var processingAudioStream: AudioEffectInputStream? = null
    private var currentSoundInstance: SoundInstance? = null

    @Volatile
    private var playState = PlayState.STOPPED

    @Volatile
    private var isInitialized = false

    val state: PlayState get() = playState
    val initialized: Boolean get() = isInitialized

    private var currentUrl: String? = null
    private var currentEffectChain = EffectChain.empty()

    private val soundManager: net.minecraft.client.sounds.SoundManager
        get() = Minecraft.getInstance().soundManager


    fun startAudio(offsetSeconds: Double = 0.0) {
        launchModCoroutine {
            stateMutex.withLock {
                val url = currentUrl ?: return@launchModCoroutine
                // If already playing the same URL, ignore
                if (playState == PlayState.PLAYING && isInitialized) {
                    return@launchModCoroutine
                }

                playState = PlayState.PLAYING

                try {
                    val audioSource = resolveAudioSource(url) ?: run {
                        Logger.err("AudioPlayer $playerId: Failed to resolve audio source for URL: $url")
                        resetState()
                        return@launchModCoroutine
                    }

                    val newStream = makeStream(audioSource, currentEffectChain, offsetSeconds) ?: run {
                        Logger.err("AudioPlayer $playerId: Failed to create audio stream")
                        resetState()
                        return@launchModCoroutine
                    }

                    processingAudioStream = newStream
                    currentSoundInstance = soundInstanceProvider(playerId, newStream)

                    withClientContext {
                        currentSoundInstance?.let {
                            soundManager.play(it)
                            isInitialized = true
                            Logger.info("AudioPlayer $playerId: Successfully started playback with offset: ${offsetSeconds}s")
                        } ?: run {
                            Logger.err("AudioPlayer $playerId: Failed to create sound instance")
                            resetState()
                        }
                    }
                } catch (e: Exception) {
                    Logger.err("AudioPlayer $playerId: Error during playback initialization: ${e.message}")
                    e.printStackTrace()
                    resetState()
                }
            }
        }
    }

    fun play(
        url: String,
        effectChain: EffectChain = EffectChain.empty(),
    ) {
        Logger.info("REQUEST TO PLAY WITH PLAYERID $playerId")
        val currentGamePlayer = Minecraft.getInstance().player ?: return

        currentUrl = url
        currentEffectChain = effectChain

        ModNetworkHandler.channel.sendToServer(
            RequestPlayerLobbyPacket(currentGamePlayer.stringUUID, playerId)
        )
    }

    fun stop() {
        launchModCoroutine {
            stateMutex.withLock {
                if (playState == PlayState.STOPPED) {
                    Logger.info("AudioPlayer $playerId: Already stopped")
                    return@launchModCoroutine
                }

                try {
                    withClientContext {
                        currentSoundInstance?.let { soundManager.stop(it) }
                    }

                    cleanupResources()
                    Logger.info("AudioPlayer $playerId: Successfully stopped playback")
                } catch (e: Exception) {
                    Logger.err("AudioPlayer $playerId: Error during stop: ${e.message}")
                    e.printStackTrace()
                    // Still try to clean up resources
                    cleanupResources()
                }
            }
        }
    }

    fun pause() {
        launchModCoroutine {
            stateMutex.withLock {
                if (playState == PlayState.PAUSED) {
                    Logger.info("AudioPlayer $playerId: Already paused")
                    return@launchModCoroutine
                }

                try {
                    withClientContext {
                        currentSoundInstance?.let { soundManager.stop(it) }
                    }
                    playState = PlayState.PAUSED
                    Logger.info("AudioPlayer $playerId: Paused playback")
                } catch (e: Exception) {
                    Logger.err("AudioPlayer $playerId: Error during pause: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    fun resume() {
        launchModCoroutine {
            stateMutex.withLock {
                if (playState == PlayState.PLAYING) {
                    Logger.info("AudioPlayer $playerId: Already playing")
                    return@launchModCoroutine
                }

                try {
                    withClientContext {
                        currentSoundInstance?.let { soundManager.play(it) }
                    }
                    playState = PlayState.PLAYING
                    Logger.info("AudioPlayer $playerId: Resumed playback")
                } catch (e: Exception) {
                    Logger.err("AudioPlayer $playerId: Error during resume: ${e.message}")
                    e.printStackTrace()
                }
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
        seekSeconds: Double,
    ): AudioEffectInputStream? {
        if (processingAudioStream != null) {
            return processingAudioStream
        }

        try {
            val effectiveUrl = audioSource.resolveAudioUrl()
            ffmpegExecutor.createStream(effectiveUrl, sampleRate, seekSeconds)

            val inputStream = ffmpegExecutor.inputStream ?: run {
                Logger.err("AudioPlayer $playerId: FFmpeg input stream is null")
                return null
            }

            val stream = AudioEffectInputStream(
                inputStream,
                effectChain,
                sampleRate,
                onStreamEnd = {
                    // Stream has ended naturally, transition to STOPPED state
                    Logger.info("AudioPlayer $playerId: Stream ended naturally")
                    launchModCoroutine {
                        stateMutex.withLock {
                            if (playState == PlayState.PLAYING) {
                                cleanupResources()
                                Logger.info("AudioPlayer $playerId: Transitioned to STOPPED after stream end")
                            }
                        }
                    }
                }
            )
            return stream
        } catch (e: Exception) {
            Logger.err("AudioPlayer $playerId: Error creating stream: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    private fun cleanupResources() {
        try {
            processingAudioStream?.close()
            processingAudioStream = null
            currentSoundInstance = null
            ffmpegExecutor.destroy()
            playState = PlayState.STOPPED
            isInitialized = false
        } catch (e: Exception) {
            Logger.err("AudioPlayer $playerId: Error during cleanup: ${e.message}")
        }
    }

    private fun resetState() {
        playState = PlayState.STOPPED
        isInitialized = false
        cleanupResources()
    }
}