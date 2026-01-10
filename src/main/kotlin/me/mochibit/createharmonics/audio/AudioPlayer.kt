package me.mochibit.createharmonics.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.mochibit.createharmonics.CommonConfig
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.audio.comp.SoundEventComposition
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.process.FFmpegExecutor
import me.mochibit.createharmonics.audio.source.AudioSource
import me.mochibit.createharmonics.audio.source.HttpAudioSource
import me.mochibit.createharmonics.audio.source.YoutubeAudioSource
import me.mochibit.createharmonics.audio.stream.AudioEffectInputStream
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
 * - Multiple audio sources (YouTube, HTTP, direct streams)
 * - Audio effect chains
 * - Playback control (play, pause, resume, stop)
 * - Automatic stream cleanup
 * - Thread-safe state management
 *
 * @param soundInstanceProvider Provider for instancing the correct Minecraft's sound instance
 * @param playerId ID associated to the current audio player, must be unique
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

    /**
     * Sealed class representing different playback sources
     */
    private sealed class PlaybackSource {
        data class Url(
            val url: String,
        ) : PlaybackSource()

        data class Stream(
            val stream: InputStream,
            val audioName: String = "Stream",
        ) : PlaybackSource()
    }

    /**
     * Holds all the current playback state and resources
     */
    private data class PlaybackContext(
        val source: PlaybackSource,
        val effectChain: EffectChain,
        val soundComposition: SoundEventComposition,
        val offsetSeconds: Double,
        var ffmpegExecutor: FFmpegExecutor? = null,
        var processingAudioStream: AudioEffectInputStream? = null,
        var soundInstance: SoundInstance? = null,
    ) {
        fun cleanup() {
            // Order is important: composition -> sound -> streams -> ffmpeg
            try {
                soundComposition.stopComposition()
            } catch (e: Exception) {
                Logger.err("Error stopping composition: ${e.message}")
            }

            try {
                processingAudioStream?.close()
            } catch (e: Exception) {
                Logger.err("Error closing audio stream: ${e.message}")
            }

            try {
                ffmpegExecutor?.destroy()
            } catch (e: Exception) {
                Logger.err("Error destroying FFmpeg: ${e.message}")
            }
        }
    }

    private val stateMutex = Mutex()

    @Volatile
    private var playState = PlayState.STOPPED

    @Volatile
    private var playbackContext: PlaybackContext? = null

    val state: PlayState
        get() = playState

    private val soundManager: net.minecraft.client.sounds.SoundManager
        get() = Minecraft.getInstance().soundManager

    /**
     * Start playing audio from the specified URL.
     *
     * If already playing different content, the current playback will be stopped
     * and the new audio will start. If the same content is already playing, this is a no-op.
     *
     * @param url The audio URL to play (YouTube or HTTP)
     * @param effectChain Chain of audio effects to apply
     * @param soundEventComposition Composition of sound events
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
                val source = PlaybackSource.Url(url)

                if (isAlreadyPlayingSameContent(source, effectChain)) {
                    return@launchModCoroutine
                }

                if (playState == PlayState.LOADING) {
                    val currentSource = playbackContext?.source
                    if (currentSource == source && playbackContext?.effectChain == effectChain) {
                        return@launchModCoroutine
                    }
                }

                if (playState != PlayState.STOPPED) {
                    cleanupResourcesInternal()
                }

                playState = PlayState.LOADING
                val context = PlaybackContext(source, effectChain, soundEventComposition, offsetSeconds)
                playbackContext = context

                runCatching {
                    initializeUrlPlayback(url, context)
                }.onSuccess {
                    playState = PlayState.PLAYING
                }.onFailure { e ->
                    Logger.err("AudioPlayer $playerId: Error during playback: ${e.message}")
                    e.printStackTrace()
                    resetStateInternal()
                    notifyStreamFailure()
                }
            }
        }
    }

    /**
     * Start playing audio from a direct input stream.
     * This bypasses FFmpeg and plays the stream directly with optional effects.
     *
     * @param inputStream The raw audio input stream (should be PCM format at the specified sample rate)
     * @param audioName Optional name for the audio source
     * @param effectChain Chain of audio effects to apply
     * @param soundEventComposition Composition of sound events
     * @param offsetSeconds Starting offset in seconds to skip in the stream
     */
    fun playFromStream(
        inputStream: InputStream,
        audioName: String = "Stream",
        effectChain: EffectChain = EffectChain.empty(),
        soundEventComposition: SoundEventComposition = SoundEventComposition(),
        offsetSeconds: Double = 0.0,
    ) {
        launchModCoroutine(Dispatchers.IO) {
            stateMutex.withLock {
                val source = PlaybackSource.Stream(inputStream, audioName)

                if (playState != PlayState.STOPPED) {
                    cleanupResourcesInternal()
                }

                playState = PlayState.LOADING
                val context = PlaybackContext(source, effectChain, soundEventComposition, offsetSeconds)
                playbackContext = context

                runCatching {
                    initializeStreamPlayback(inputStream, audioName, context)
                }.onSuccess {
                    playState = PlayState.PLAYING
                }.onFailure { e ->
                    Logger.err("AudioPlayer $playerId: Error during stream playback: ${e.message}")
                    e.printStackTrace()
                    resetStateInternal()
                    notifyStreamFailure()
                }
            }
        }
    }

    private fun isAlreadyPlayingSameContent(
        source: PlaybackSource,
        effectChain: EffectChain,
    ): Boolean {
        if (playState != PlayState.PLAYING) return false
        val context = playbackContext ?: return false
        return context.source == source && context.effectChain == effectChain
    }

    private suspend fun initializeUrlPlayback(
        url: String,
        context: PlaybackContext,
    ) {
        val audioSource =
            resolveAudioSource(url)
                ?: throw IllegalArgumentException("Unsupported audio source for URL: $url")

        // Update audio name if available
        val audioName = audioSource.getAudioName()
        if (audioName != "Unknown") {
            ModPackets.channel.sendToServer(UpdateAudioNamePacket(playerId, audioName))
        }

        // Validate offset against duration
        val duration = audioSource.getDurationSeconds()
        if (duration > 0 && context.offsetSeconds >= duration) {
            throw IllegalArgumentException(
                "Offset (${context.offsetSeconds}s) exceeds duration (${duration}s)",
            )
        }

        // Initialize FFmpeg executor
        val ffmpegExecutor = FFmpegExecutor()
        context.ffmpegExecutor = ffmpegExecutor

        val effectiveUrl = audioSource.resolveAudioUrl()
        if (!ffmpegExecutor.createStream(effectiveUrl, sampleRate, context.offsetSeconds)) {
            throw IllegalStateException("FFmpeg stream initialization failed")
        }

        val rawInputStream =
            ffmpegExecutor.inputStream
                ?: throw IllegalStateException("FFmpeg input stream is null")

        // Create effect stream and start playback
        val audioStream = createAudioEffectInputStream(rawInputStream, context)
        context.processingAudioStream = audioStream

        if (!audioStream.awaitPreBuffering()) {
            throw IllegalStateException("Pre-buffering timeout")
        }

        startPlayback(audioStream, context)
    }

    private suspend fun initializeStreamPlayback(
        inputStream: InputStream,
        audioName: String,
        context: PlaybackContext,
    ) {
        // Update audio name
        if (audioName != "Unknown" && audioName.isNotBlank()) {
            ModPackets.channel.sendToServer(UpdateAudioNamePacket(playerId, audioName))
        }

        // Skip bytes if offset is specified
        if (context.offsetSeconds > 0.0) {
            // For 48kHz mono PCM: 48000 samples/sec * 2 bytes/sample = 96000 bytes/sec
            val bytesPerSecond = sampleRate * 2L // 2 bytes per sample for 16-bit PCM
            var bytesToSkip = (context.offsetSeconds * bytesPerSecond).toLong()

            // CRITICAL: Ensure we skip an even number of bytes to maintain sample alignment
            // 16-bit PCM samples are 2 bytes each, so we must skip in multiples of 2
            if (bytesToSkip % 2 != 0L) {
                bytesToSkip += 1 // Round up to next even number
                Logger.warn("AudioPlayer $playerId: Adjusted skip to $bytesToSkip bytes for sample alignment")
            }

            var totalSkipped = 0L
            val skipBuffer = ByteArray(8192) // Use read buffer instead of skip() for reliability

            withContext(Dispatchers.IO) {
                var remaining = bytesToSkip

                while (remaining > 0) {
                    val toRead = minOf(remaining, skipBuffer.size.toLong()).toInt()
                    val bytesRead = inputStream.read(skipBuffer, 0, toRead)

                    if (bytesRead <= 0) {
                        // End of stream or read error
                        Logger.warn("AudioPlayer $playerId: Could only skip $totalSkipped of $bytesToSkip bytes (reached end of stream)")
                        break
                    }

                    totalSkipped += bytesRead
                    remaining -= bytesRead
                }
            }

            if (totalSkipped > 0) {
                val secondsSkipped = totalSkipped / bytesPerSecond.toDouble()
                Logger.info("AudioPlayer $playerId: Skipped $totalSkipped bytes ($secondsSkipped seconds) from input stream")

                // Verify alignment was maintained
                if (totalSkipped % 2 != 0L) {
                    Logger.err("AudioPlayer $playerId: WARNING - Sample misalignment detected! Skipped odd number of bytes: $totalSkipped")
                }
            }
        }

        // Create effect stream directly from input (no FFmpeg)
        val audioStream = createAudioEffectInputStream(inputStream, context)
        context.processingAudioStream = audioStream

        if (!audioStream.awaitPreBuffering()) {
            throw IllegalStateException("Pre-buffering timeout")
        }

        startPlayback(audioStream, context)
    }

    private fun createAudioEffectInputStream(
        inputStream: InputStream,
        context: PlaybackContext,
    ): AudioEffectInputStream =
        AudioEffectInputStream(
            inputStream,
            context.effectChain,
            sampleRate,
            onStreamEnd = { handleStreamEnd() },
            onStreamHang = { handleStreamHang() },
        )

    private suspend fun startPlayback(
        audioStream: AudioEffectInputStream,
        context: PlaybackContext,
    ) {
        val soundInstance = soundInstanceProvider(playerId, audioStream)
        context.soundInstance = soundInstance

        withClientContext {
            context.soundComposition.makeComposition(soundInstance)
            soundManager.play(soundInstance)
        }
    }

    private fun handleStreamEnd() {
        launchModCoroutine {
            stateMutex.withLock {
                if (playState == PlayState.PLAYING) {
                    cleanupResourcesInternal()
                    notifyStreamEnd()
                }
            }
        }
    }

    private fun handleStreamHang() {
        if (playState == PlayState.PLAYING) {
            launchModCoroutine {
                playbackContext?.soundInstance?.let { soundInstance ->
                    soundManager.play(soundInstance)
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
                if (playState == PlayState.STOPPED) return@launchModCoroutine

                runCatching {
                    withClientContext {
                        playbackContext?.let { context ->
                            context.soundInstance?.let { soundManager.stop(it) }
                        }
                    }
                    cleanupResourcesInternal()
                }.onSuccess {
                }.onFailure { e ->
                    Logger.err("AudioPlayer $playerId: Error during stop: ${e.message}")
                    e.printStackTrace()
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
                if (playState != PlayState.PLAYING) return@launchModCoroutine

                runCatching {
                    withClientContext {
                        playbackContext?.let { context ->
                            context.soundInstance?.let { soundManager.stop(it) }
                            context.soundComposition.stopComposition()
                        }
                    }
                    playState = PlayState.PAUSED
                }.onSuccess {
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
                if (playState != PlayState.PAUSED) return@launchModCoroutine

                val context = playbackContext
                if (context?.soundInstance == null) {
                    Logger.err("AudioPlayer $playerId: Cannot resume, no active sound instance")
                    return@launchModCoroutine
                }

                runCatching {
                    withClientContext {
                        context.soundInstance?.let { soundInstance ->
                            soundManager.play(soundInstance)
                            context.soundComposition.makeComposition(soundInstance)
                        }
                    }
                    playState = PlayState.PLAYING
                }.onSuccess {
                }.onFailure { e ->
                    Logger.err("AudioPlayer $playerId: Error during resume: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    /** TODO Integrate this to audioSources directly
     * HTTP integration is discontinued, due to some possible vulnerabilities
     */
    @Deprecated("This will be removed, audio source will validate the url")
    private fun resolveAudioSource(url: String): AudioSource? =
        when {
            url.contains("youtube.com") || url.contains("youtu.be") -> {
                YoutubeAudioSource(url)
            }

            else -> {
                null
            }
        }

    /**
     * Internal cleanup - must be called within stateMutex.withLock
     */
    private fun cleanupResourcesInternal() {
        playbackContext?.cleanup()
        playbackContext = null
        playState = PlayState.STOPPED
    }

    /**
     * Internal state reset - must be called within stateMutex.withLock
     */
    private fun resetStateInternal() {
        cleanupResourcesInternal()
        playState = PlayState.STOPPED
    }

    private fun notifyStreamEnd() {
        ModPackets.channel.sendToServer(AudioPlayerStreamEndPacket(playerId))
    }

    private fun notifyStreamFailure() {
        ModPackets.channel.sendToServer(AudioPlayerStreamEndPacket(playerId))
    }

    /**
     * Dispose of this audio player and release all resources.
     * Should be called when the player is no longer needed.
     */
    fun dispose() {
        stopSoundImmediately()

        launchModCoroutine {
            stateMutex.withLock {
                cleanupResourcesInternal()
            }
        }
    }

    /**
     * Synchronously stop the sound immediately, without waiting for coroutines.
     * This is useful for cases where immediate cleanup is required.
     */
    fun stopSoundImmediately() {
        try {
            val context = playbackContext
            if (context != null) {
                // Always stop composition first, even if other operations fail
                try {
                    context.soundComposition.stopComposition()
                } catch (e: Exception) {
                    Logger.err("AudioPlayer $playerId: Error stopping composition: ${e.message}")
                }

                // Try to stop the sound instance
                try {
                    context.soundInstance?.let { soundManager.stop(it) }
                } catch (e: Exception) {
                    Logger.err("AudioPlayer $playerId: Error stopping sound: ${e.message}")
                }

                // Cleanup resources
                try {
                    context.cleanup()
                } catch (e: Exception) {
                    Logger.err("AudioPlayer $playerId: Error during cleanup: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Logger.err("AudioPlayer $playerId: Error in stopSoundImmediately: ${e.message}")
        }
    }
}
