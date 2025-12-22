package me.mochibit.createharmonics.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.mochibit.createharmonics.CommonConfig
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.audio.comp.SoundEventComposition
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.process.FFmpegExecutor
import me.mochibit.createharmonics.audio.source.AudioSource
import me.mochibit.createharmonics.audio.source.HttpAudioSource
import me.mochibit.createharmonics.audio.source.YoutubeAudioSource
import me.mochibit.createharmonics.coroutine.launchModCoroutine
import me.mochibit.createharmonics.coroutine.withClientContext
import me.mochibit.createharmonics.network.packet.AudioPlayerStreamEndPacket
import me.mochibit.createharmonics.network.packet.UpdateAudioNamePacket
import me.mochibit.createharmonics.registry.ModPackets
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import java.io.InputStream
import java.util.UUID

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
    val sampleRate: Int = 48000,
) {
    /**
     * Represents the current playback state of the audio player.
     */
    enum class PlayState {
        /** No audio is playing and resources are released */
        STOPPED,

        /** Audio is being initialized and will start playing soon */
        LOADING,

        /** Audio is currently playing */
        PLAYING,

        /** Audio is paused and can be resumed */
        PAUSED,
    }

    private val stateMutex = Mutex()
    private val ffmpegExecutor = FFmpegExecutor()
    private var processingAudioStream: AudioEffectInputStream? = null

    @Volatile
    private var currentSoundInstance: SoundInstance? = null

    @Volatile
    private var currentSoundComposition: SoundEventComposition? = null

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
        soundEventComposition: SoundEventComposition = SoundEventComposition(),
        offsetSeconds: Double = 0.0,
    ) {
        if (url.isBlank()) {
            Logger.err("AudioPlayer $playerId: Cannot play empty URL")
            return
        }

        launchModCoroutine(Dispatchers.IO) {
            stateMutex.withLock {
                if (isAlreadyPlayingSameContent(url, effectChain)) {
                    Logger.info("AudioPlayer $playerId: Already playing requested URL")
                    return@launchModCoroutine
                }

                // Check if already loading the same content
                if (playState == PlayState.LOADING && currentUrl == url && currentEffectChain == effectChain) {
                    Logger.info("AudioPlayer $playerId: Already loading requested URL")
                    return@launchModCoroutine
                }

                if (playState != PlayState.STOPPED) {
                    cleanupResourcesInternal()
                }

                updatePlaybackConfiguration(url, effectChain, soundEventComposition, offsetSeconds)
                playState = PlayState.LOADING

                val playbackResult =
                    runCatching {
                        initializePlayback(url, effectChain, soundEventComposition, offsetSeconds)
                    }

                if (playbackResult.isFailure) {
                    Logger.err("AudioPlayer $playerId: Error during playback initialization: ${playbackResult.exceptionOrNull()?.message}")
                    playbackResult.exceptionOrNull()?.printStackTrace()

                    try {
                        resetStateInternal()
                    } catch (e: Exception) {
                        Logger.err("AudioPlayer $playerId: Error during state reset: ${e.message}")
                        e.printStackTrace()
                    }

                    handleStreamFailure()
                }
            }
        }
    }

    private fun isAlreadyPlayingSameContent(
        url: String,
        effectChain: EffectChain,
    ): Boolean = playState == PlayState.PLAYING && currentUrl == url && currentEffectChain == effectChain

    private fun updatePlaybackConfiguration(
        url: String,
        effectChain: EffectChain,
        soundEventComposition: SoundEventComposition,
        offsetSeconds: Double,
    ) {
        currentUrl = url
        currentEffectChain = effectChain
        currentOffsetSeconds = offsetSeconds
        currentSoundComposition = soundEventComposition
    }

    private suspend fun initializePlayback(
        url: String,
        effectChain: EffectChain,
        soundEventComposition: SoundEventComposition,
        offsetSeconds: Double,
    ) {
        val audioSource =
            resolveAudioSource(url) ?: run {
                Logger.err("AudioPlayer $playerId: Failed to resolve audio source for URL: $url")
                throw IllegalArgumentException("Unsupported audio source")
            }

        audioSource.getAudioName().let { audioName ->
            if (audioName != "Unknown") {
                ModPackets.channel.sendToServer(
                    UpdateAudioNamePacket(playerId, audioName),
                )
            }
        }

        val duration = audioSource.getDurationSeconds()
        if (duration > 0 && offsetSeconds >= duration) {
            Logger.err("AudioPlayer $playerId: Offset ($offsetSeconds s) exceeds or equals duration ($duration s). Resetting playback.")
            throw IllegalArgumentException("Offset exceeds audio duration")
        }

        val effectiveUrl = audioSource.resolveAudioUrl()
        if (!ffmpegExecutor.createStream(effectiveUrl, sampleRate, offsetSeconds)) {
            Logger.err("AudioPlayer $playerId: FFmpeg stream failed to start")
            throw IllegalStateException("FFmpeg stream initialization failed")
        }

        val inputStream =
            ffmpegExecutor.inputStream
                ?: throw IllegalStateException("FFmpeg input stream is null")

        val audioStream = createAudioEffectInputStream(inputStream, effectChain)
        processingAudioStream = audioStream

        if (!audioStream.awaitPreBuffering()) {
            Logger.err("AudioPlayer $playerId: Pre-buffering timeout")
            throw IllegalStateException("Pre-buffering timeout")
        }

        startPlayback(audioStream, url, soundEventComposition, offsetSeconds)
    }

    private fun createAudioEffectInputStream(
        inputStream: InputStream,
        effectChain: EffectChain,
    ): AudioEffectInputStream =
        AudioEffectInputStream(
            inputStream,
            effectChain,
            sampleRate,
            onStreamEnd = { handleStreamEnd() },
            onStreamHang = { handleStreamHang() },
        )

    private fun handleStreamFailure() {
        Logger.info("AudioPlayer $playerId: Sending stream end packet to server due to failure")
        ModPackets.channel.sendToServer(
            AudioPlayerStreamEndPacket(playerId),
        )
    }

    private fun handleStreamEnd() {
        Logger.info("AudioPlayer $playerId: Stream ended naturally")
        launchModCoroutine {
            stateMutex.withLock {
                if (playState == PlayState.PLAYING) {
                    cleanupResourcesInternal()
                    ModPackets.channel.sendToServer(
                        AudioPlayerStreamEndPacket(playerId),
                    )
                }
            }
        }
    }

    private fun handleStreamHang() {
        if (playState == PlayState.PLAYING) {
            launchModCoroutine {
                currentSoundInstance?.let { soundInstance ->
                    soundManager.play(soundInstance)
                }
            }
        }
    }

    private suspend fun startPlayback(
        audioStream: AudioEffectInputStream,
        url: String,
        soundEventComposition: SoundEventComposition,
        offsetSeconds: Double,
    ) {
        currentSoundInstance = soundInstanceProvider(playerId, audioStream)
        playState = PlayState.PLAYING

        withClientContext {
            currentSoundInstance?.let { soundInstance ->
                soundEventComposition.makeComposition(soundInstance)
                soundManager.play(soundInstance)
                Logger.info("AudioPlayer $playerId: Successfully started playback (URL: $url, offset: ${offsetSeconds}s)")
            } ?: throw IllegalStateException("Failed to create sound instance")
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

                runCatching {
                    withClientContext {
                        // Stop composition FIRST before stopping the main sound instance
                        currentSoundComposition?.let {
                            try {
                                it.stopComposition()
                            } catch (e: Exception) {
                                Logger.err("AudioPlayer $playerId: Error stopping composition in stop(): ${e.message}")
                            }
                        }

                        currentSoundInstance?.let {
                            soundManager.stop(it)
                        }
                    }
                    cleanupResourcesInternal()
                }.onSuccess {
                    Logger.info("AudioPlayer $playerId: Successfully stopped playback")
                }.onFailure { e ->
                    Logger.err("AudioPlayer $playerId: Error during stop: ${e.message}")
                    e.printStackTrace()
                    cleanupResourcesInternal() // Ensure cleanup even on error
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

                runCatching {
                    withClientContext {
                        currentSoundInstance?.let {
                            soundManager.stop(it)
                            // TODO: Maybe handle differently based on each soundevent definition, if it should stop on pause
                            currentSoundComposition?.stopComposition()
                        }
                    }
                    playState = PlayState.PAUSED
                }.onSuccess {
                    Logger.info("AudioPlayer $playerId: Paused playback")
                }.onFailure { e ->
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

                runCatching {
                    withClientContext {
                        currentSoundInstance?.let {
                            soundManager.play(it)
                            currentSoundComposition?.makeComposition(it)
                        }
                    }
                    playState = PlayState.PLAYING
                }.onSuccess {
                    Logger.info("AudioPlayer $playerId: Resumed playback")
                }.onFailure { e ->
                    Logger.err("AudioPlayer $playerId: Error during resume: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    private fun resolveAudioSource(url: String): AudioSource? =
        when {
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

    /**
     * Internal cleanup method - must be called within stateMutex.withLock
     */
    private fun cleanupResourcesInternal() {
        // Stop composition FIRST before any other cleanup to ensure sounds are stopped
        try {
            currentSoundComposition?.stopComposition()
        } catch (e: Exception) {
            Logger.err("AudioPlayer $playerId: Error stopping sound composition: ${e.message}")
        }
        currentSoundComposition = null

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
     * This first stops the sound immediately, then cleans up resources asynchronously.
     */
    fun dispose() {
        // First, immediately stop the sound without waiting for the mutex
        stopSoundImmediately()

        // Then perform full cleanup asynchronously with proper synchronization
        launchModCoroutine {
            stateMutex.withLock {
                cleanupResourcesInternal()
                Logger.info("AudioPlayer $playerId: Disposed")
            }
        }
    }

    /**
     * Synchronously stop the sound immediately, without waiting for coroutines.
     * This is useful for cases where immediate cleanup is required (e.g., when a contraption is destroyed).
     * This method can be called from any thread and does not require acquiring the state mutex.
     * Full cleanup still happens asynchronously via the dispose() method.
     */
    fun stopSoundImmediately() {
        try {
            // Stop composition FIRST to ensure all composition sounds are stopped
            val composition = currentSoundComposition
            if (composition != null) {
                try {
                    composition.stopComposition()
                } catch (e: Exception) {
                    Logger.err("AudioPlayer $playerId: Error stopping composition immediately: ${e.message}")
                }
            }

            // Then stop the main sound instance
            val soundInstance = currentSoundInstance
            if (soundInstance != null) {
                try {
                    soundManager.stop(soundInstance)
                } catch (e: Exception) {
                    Logger.err("AudioPlayer $playerId: Error stopping sound instance immediately: ${e.message}")
                }
            }

            if (composition != null || soundInstance != null) {
                Logger.info("AudioPlayer $playerId: Sound stopped immediately")
            }
        } catch (e: Exception) {
            Logger.err("AudioPlayer $playerId: Error stopping sound immediately: ${e.message}")
        }
    }
}
