package me.mochibit.createharmonics.audio

import kotlinx.coroutines.Dispatchers
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
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import java.io.InputStream
import java.util.*

typealias StreamId = String
typealias StreamingSoundInstanceProvider = (streamId: StreamId, stream: InputStream) -> SoundInstance

/**
 * Responsible for playing custom sound sources to the Minecraft engine.
 *
 * This class manages audio playback with support for:
 * - Multiple audio sources (YouTube, HTTP)
 * - Audio effect chains
 * - Playback control (play, pause, resume, stop)
 * - Automatic stream cleanup
 * - Thread-safe state management
 *
 * @param soundInstanceProvider Provider for instancing the correct Minecraft's sound instance, to be used along its sound engine
 * @param playerId ID associated to the current audio player, must be unique every time
 * @param sampleRate Sample rate of the streamed audio, default is 48000hz
 */
class AudioPlayer(
    val soundInstanceProvider: StreamingSoundInstanceProvider,
    val playerId: String = UUID.randomUUID().toString(),
    val sampleRate: Int = 48000
) {
    /**
     * Represents the current playback state of the audio player.
     */
    enum class PlayState {
        /** No audio is playing and resources are released */
        STOPPED,

        /** Audio is currently playing */
        PLAYING,

        /** Audio is paused and can be resumed */
        PAUSED,
    }

    private val stateMutex = Mutex()
    private val ffmpegExecutor = FFmpegExecutor()
    private var processingAudioStream: AudioEffectInputStream? = null
    private var currentSoundInstance: SoundInstance? = null

    @Volatile
    private var playState = PlayState.STOPPED

    val state: PlayState
        get() = playState

    private var currentUrl: String? = null
    private var currentEffectChain = EffectChain.empty()
    private var currentOffsetSeconds: Double = 0.0

    private val soundManager: net.minecraft.client.sounds.SoundManager
        get() = Minecraft.getInstance().soundManager

    /**
     * Start playing audio from the specified URL.
     *
     * If already playing a different URL or with different effects, the current playback will be stopped
     * and the new audio will start. If the same URL with same effects is already playing, this is a no-op.
     *
     * @param url The audio URL to play (YouTube or HTTP)
     * @param effectChain Chain of audio effects to apply
     * @param offsetSeconds Starting offset in seconds
     */
    fun play(
        url: String,
        effectChain: EffectChain = EffectChain.empty(),
        offsetSeconds: Double = 0.0
    ) {
        if (url.isBlank()) {
            Logger.err("AudioPlayer $playerId: Cannot play empty URL")
            return
        }

        launchModCoroutine(Dispatchers.IO) {
            stateMutex.withLock {
                // If already playing the same URL with same effects, ignore
                if (playState == PlayState.PLAYING &&
                    currentUrl == url &&
                    currentEffectChain == effectChain
                ) {
                    Logger.info("AudioPlayer $playerId: Already playing requested URL")
                    return@launchModCoroutine
                }

                // Stop any existing playback before starting new one
                if (playState != PlayState.STOPPED) {
                    cleanupResourcesInternal()
                }

                // Update current configuration
                currentUrl = url
                currentEffectChain = effectChain
                currentOffsetSeconds = offsetSeconds

                try {
                    val audioSource = resolveAudioSource(url) ?: run {
                        Logger.err("AudioPlayer $playerId: Failed to resolve audio source for URL: $url")
                        resetStateInternal()
                        return@launchModCoroutine
                    }

                    // Start FFmpeg process in background first (don't block here)
                    val effectiveUrl = audioSource.resolveAudioUrl()
                    ffmpegExecutor.createStream(effectiveUrl, sampleRate, offsetSeconds)

                    // Give FFmpeg more time to start and begin connecting
                    kotlinx.coroutines.delay(500)

                    val inputStream = ffmpegExecutor.inputStream ?: run {
                        Logger.err("AudioPlayer $playerId: FFmpeg input stream is null")
                        resetStateInternal()
                        return@launchModCoroutine
                    }

                    // Now create the audio stream (pre-buffering happens in background)
                    // This is still on IO dispatcher, so pre-buffering won't block game thread
                    val newStream = AudioEffectInputStream(
                        inputStream,
                        effectChain,
                        sampleRate,
                        onStreamEnd = {
                            Logger.info("AudioPlayer $playerId: Stream ended naturally")
                            launchModCoroutine {
                                stateMutex.withLock {
                                    if (playState == PlayState.PLAYING) {
                                        cleanupResourcesInternal()
                                        Logger.info("AudioPlayer $playerId: Transitioned to STOPPED after stream end")
                                    }
                                }
                            }
                        },
                        onStreamHang = {
                            if (playState == PlayState.PLAYING) {
                                launchModCoroutine {
                                    currentSoundInstance?.let { soundInstance ->
                                        soundManager.stop(soundInstance)
                                        soundManager.play(soundInstance)
                                    }
                                }
                            }
                        }
                    )

                    // Give pre-buffering thread a bit more time to fill the buffer
                    kotlinx.coroutines.delay(200)

                    playState = PlayState.PLAYING

                    processingAudioStream = newStream
                    currentSoundInstance = soundInstanceProvider(playerId, newStream)

                    withClientContext {
                        currentSoundInstance?.let { soundInstance ->
                            soundManager.play(soundInstance)
                            Logger.info("AudioPlayer $playerId: Successfully started playback (URL: $url, offset: ${offsetSeconds}s)")
                        } ?: run {
                            Logger.err("AudioPlayer $playerId: Failed to create sound instance")
                            resetStateInternal()
                        }
                    }
                } catch (e: Exception) {
                    Logger.err("AudioPlayer $playerId: Error during playback initialization: ${e.message}")
                    e.printStackTrace()
                    resetStateInternal()
                }
            }
        }
    }

    /**
     * Stop playback and release all resources.
     * Transitions to STOPPED state.
     */
    fun stop() {
        launchModCoroutine(Dispatchers.IO) {
            stateMutex.withLock {
                if (playState == PlayState.STOPPED) {
                    return@launchModCoroutine
                }

                try {
                    withClientContext {
                        currentSoundInstance?.let { soundManager.stop(it) }
                    }

                    cleanupResourcesInternal()
                    Logger.info("AudioPlayer $playerId: Successfully stopped playback")
                } catch (e: Exception) {
                    Logger.err("AudioPlayer $playerId: Error during stop: ${e.message}")
                    e.printStackTrace()
                    // Still try to clean up resources
                    cleanupResourcesInternal()
                }
            }
        }
    }

    /**
     * Pause the current playback.
     * Only works when in PLAYING state. Can be resumed later.
     */
    fun pause() {
        launchModCoroutine {
            stateMutex.withLock {
                if (playState != PlayState.PLAYING) {
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

    /**
     * Resume playback from a paused state.
     * Only works when in PAUSED state.
     */
    fun resume() {
        launchModCoroutine {
            stateMutex.withLock {
                if (playState != PlayState.PAUSED) {
                    return@launchModCoroutine
                }

                if (currentSoundInstance == null || currentUrl == null) {
                    Logger.err("AudioPlayer $playerId: Cannot resume, no active sound instance")
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


    /**
     * Internal cleanup method - must be called within stateMutex.withLock
     */
    private fun cleanupResourcesInternal() {
        try {
            processingAudioStream?.close()
        } catch (e: Exception) {
            Logger.err("AudioPlayer $playerId: Error closing audio stream: ${e.message}")
        }

        try {
            ffmpegExecutor.destroy()
        } catch (e: Exception) {
            Logger.err("AudioPlayer $playerId: Error destroying FFmpeg executor: ${e.message}")
        }

        processingAudioStream = null
        currentSoundInstance = null
        playState = PlayState.STOPPED
    }

    /**
     * Internal state reset - must be called within stateMutex.withLock
     */
    private fun resetStateInternal() {
        playState = PlayState.STOPPED
        cleanupResourcesInternal()
    }

    /**
     * Dispose of this audio player and release all resources.
     * Should be called when the player is no longer needed.
     */
    fun dispose() {
        launchModCoroutine {
            stateMutex.withLock {
                try {
                    withClientContext {
                        currentSoundInstance?.let { soundManager.stop(it) }
                    }
                } catch (e: Exception) {
                    Logger.err("AudioPlayer $playerId: Error stopping sound during dispose: ${e.message}")
                }

                cleanupResourcesInternal()
                Logger.info("AudioPlayer $playerId: Disposed")
            }
        }
    }
}