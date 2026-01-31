package me.mochibit.createharmonics.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.audio.comp.SoundEventComposition
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.instance.StreamingSoundInstance
import me.mochibit.createharmonics.audio.process.FFmpegExecutor
import me.mochibit.createharmonics.audio.source.AudioSource
import me.mochibit.createharmonics.audio.source.YoutubeAudioSource
import me.mochibit.createharmonics.audio.source.YtdlpAudioSource
import me.mochibit.createharmonics.audio.stream.AudioEffectInputStream
import me.mochibit.createharmonics.coroutine.MinecraftClientDispatcher
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
 * @param sampleRate Sample rate of the streamed audio, default is 44100hz
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
        var hasRetried: Boolean = false,
    ) {
        fun cleanup() {
            // Immediately capture all references and clear them
            // This ensures no further operations can use these resources
            val composition = soundComposition
            val executor = ffmpegExecutor
            val stream = processingAudioStream

            ffmpegExecutor = null
            processingAudioStream = null
            soundInstance = null

            launchModCoroutine(Dispatchers.IO) {
                try {
                    composition.stopComposition()
                } catch (e: Exception) {
                    Logger.err("Error stopping composition: ${e.message}")
                }

                try {
                    executor?.destroy()
                } catch (e: Exception) {
                    Logger.err("Error destroying FFmpeg: ${e.message}")
                }

                try {
                    stream?.close()
                } catch (e: Exception) {
                    Logger.err("Error closing audio stream: ${e.message}")
                }
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
            val source = PlaybackSource.Url(url)
            val context: PlaybackContext

            stateMutex.withLock {
                // Check inside lock to prevent race conditions
                if (playState == PlayState.PLAYING || playState == PlayState.LOADING) {
                    val currentSource = playbackContext?.source
                    if (currentSource == source && playbackContext?.effectChain == effectChain) {
                        return@launchModCoroutine
                    }
                }

                if (playState != PlayState.STOPPED) {
                    cleanupResourcesInternal()
                }

                playState = PlayState.LOADING
                context = PlaybackContext(source, effectChain, soundEventComposition, offsetSeconds)
                playbackContext = context
            }

            val initResult =
                runCatching {
                    initializeUrlPlayback(url, context)
                }

            stateMutex.withLock {
                if (playbackContext != context) {
                    Logger.debug("AudioPlayer $playerId: Context changed, cleaning up abandoned resources")
                    context.cleanup()
                    return@launchModCoroutine
                }

                initResult
                    .onSuccess {
                        playState = PlayState.PLAYING
                    }.onFailure { e ->
                        Logger.err("AudioPlayer $playerId: Error during playback: ${e.message}")
                        invalidateUrlCacheAndRetry(url, context)
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
        sampleRateOverride: Int? = null,
    ) {
        launchModCoroutine(Dispatchers.IO) {
            val context: PlaybackContext
            stateMutex.withLock {
                val source = PlaybackSource.Stream(inputStream, audioName)

                if (playState != PlayState.STOPPED) {
                    cleanupResourcesInternal()
                }

                playState = PlayState.LOADING
                context = PlaybackContext(source, effectChain, soundEventComposition, offsetSeconds)
                playbackContext = context
            }

            val initResult =
                runCatching {
                    initializeStreamPlayback(inputStream, audioName, context, sampleRateOverride)
                }

            stateMutex.withLock {
                // Verify we're still working with the same context
                if (playbackContext != context) {
                    Logger.debug("AudioPlayer $playerId: Context changed during stream initialization, aborting")
                    return@launchModCoroutine
                }

                initResult
                    .onSuccess {
                        playState = PlayState.PLAYING
                    }.onFailure { e ->
                        Logger.err("AudioPlayer $playerId: Error during stream playback: ${e.message}")
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

        // Wrap offset around duration if it exceeds
        val duration = audioSource.getDurationSeconds()
        val effectiveOffset =
            if (duration > 0 && context.offsetSeconds >= duration) {
                val wrappedOffset = context.offsetSeconds % duration
                Logger.info("AudioPlayer $playerId: Offset ${context.offsetSeconds}s wrapped to ${wrappedOffset}s (duration: ${duration}s)")
                wrappedOffset
            } else {
                context.offsetSeconds
            }

        // Initialize FFmpeg executor
        val ffmpegExecutor = FFmpegExecutor()
        context.ffmpegExecutor = ffmpegExecutor

        val effectiveUrl = audioSource.resolveAudioUrl()

        if (!ffmpegExecutor.createStream(effectiveUrl, sampleRate, effectiveOffset, audioSource.getHttpHeaders())) {
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

        if (playState != PlayState.LOADING) {
            throw IllegalStateException("Aborting playback")
        }

        startPlayback(audioStream, context)
    }

    /**
     * Invalidate the cached URL and retry playback once.
     * This is called when FFmpeg fails with a 403 error (expired URL).
     */
    private suspend fun invalidateUrlCacheAndRetry(
        url: String,
        context: PlaybackContext,
    ) {
        // Prevent infinite retry loops
        if (context.hasRetried) {
            Logger.err("AudioPlayer $playerId: Already retried once, giving up")
            resetStateInternal()
            notifyStreamFailure()
            return
        }

        context.hasRetried = true

        // Invalidate the cache to force fresh URL extraction
        me.mochibit.createharmonics.audio.cache.AudioInfoCache
            .invalidate(url)

        // Small delay before retry
        kotlinx.coroutines.delay(500)

        // Retry the initialization
        Logger.info("AudioPlayer $playerId: Retrying playback with fresh URL...")
        runCatching {
            initializeUrlPlayback(url, context)
        }.onSuccess {
            playState = PlayState.PLAYING
            Logger.info("AudioPlayer $playerId: Retry successful!")
        }.onFailure { e ->
            Logger.err("AudioPlayer $playerId: Retry failed: ${e.message}")
            resetStateInternal()
            notifyStreamFailure()
        }
    }

    private suspend fun initializeStreamPlayback(
        inputStream: InputStream,
        audioName: String,
        context: PlaybackContext,
        sampleRateOverride: Int?,
    ) {
        // Update audio name
        if (audioName != "Unknown" && audioName.isNotBlank()) {
            ModPackets.channel.sendToServer(UpdateAudioNamePacket(playerId, audioName))
        }

        // Skip bytes if offset is specified
        if (context.offsetSeconds > 0.0) {
            // For 48kHz mono PCM: 48000 samples/sec * 2 bytes/sample = 96000 bytes/sec
            val bytesPerSecond = (sampleRateOverride ?: sampleRate) * 2L // 2 bytes per sample for 16-bit PCM
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
                if (totalSkipped % 2 != 0L) {
                    Logger.err("AudioPlayer $playerId: WARNING - Sample misalignment detected! Skipped odd number of bytes: $totalSkipped")
                }
            }
        }

        // Create effect stream directly from input (no FFmpeg)
        val audioStream = createAudioEffectInputStream(inputStream, context, sampleRateOverride)
        context.processingAudioStream = audioStream

        if (!audioStream.awaitPreBuffering()) {
            throw IllegalStateException("Pre-buffering timeout")
        }

        if (playState != PlayState.LOADING) {
            throw IllegalStateException("Aborting playback")
        }

        startPlayback(audioStream, context, sampleRateOverride)
    }

    private fun createAudioEffectInputStream(
        inputStream: InputStream,
        context: PlaybackContext,
        sampleRateOverride: Int? = null,
    ): AudioEffectInputStream =
        AudioEffectInputStream(
            inputStream,
            context.effectChain,
            sampleRateOverride ?: sampleRate,
            onStreamEnd = { handleStreamEnd() },
            onStreamHang = { handleStreamHang() },
        )

    private suspend fun startPlayback(
        audioStream: AudioEffectInputStream,
        context: PlaybackContext,
        sampleRateOverride: Int? = null,
    ) {
        val soundInstance =
            soundInstanceProvider(playerId, audioStream)
                .apply {
                    if (this is StreamingSoundInstance) {
                        sampleRate = sampleRateOverride ?: this@AudioPlayer.sampleRate
                    }
                }
        context.soundInstance = soundInstance

        withClientContext {
            context.soundComposition.makeComposition(soundInstance)
            soundManager.play(soundInstance)
        }
    }

    private fun handleStreamEnd() {
        launchModCoroutine(Dispatchers.IO) {
            // Non-blocking tryLock with immediate bailout - no delays or retries
            if (!stateMutex.tryLock()) {
                Logger.debug("AudioPlayer $playerId: Stream end - mutex busy, ignoring duplicate event")
                return@launchModCoroutine
            }

            try {
                when (playState) {
                    PlayState.PLAYING -> {
                        cleanupResourcesInternal()
                        notifyStreamEnd()
                    }

                    PlayState.PAUSED -> {
                        cleanupResourcesInternal()
                        notifyStreamFailure()
                    }

                    PlayState.LOADING, PlayState.STOPPED -> {
                    }
                }

                playState = PlayState.STOPPED
            } finally {
                stateMutex.unlock()
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
            // Capture sound instance while holding lock briefly
            val soundInstanceToStop: SoundInstance?
            stateMutex.withLock {
                if (playState == PlayState.STOPPED) return@launchModCoroutine
                soundInstanceToStop = playbackContext?.soundInstance
                cleanupResourcesInternal()
            }

            // Stop sound outside the lock to avoid blocking
            if (soundInstanceToStop != null) {
                runCatching {
                    withClientContext {
                        soundManager.stop(soundInstanceToStop)
                    }
                }.onFailure { e ->
                    Logger.err("AudioPlayer $playerId: Error stopping sound: ${e.message}")
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
            // Capture what we need while holding lock briefly
            val soundInstanceToStop: SoundInstance?
            val compositionToStop: SoundEventComposition?
            stateMutex.withLock {
                if (playState != PlayState.PLAYING) return@launchModCoroutine
                soundInstanceToStop = playbackContext?.soundInstance
                compositionToStop = playbackContext?.soundComposition
                playState = PlayState.PAUSED
            }

            // Stop sound and composition outside the lock to avoid blocking
            runCatching {
                withClientContext {
                    soundInstanceToStop?.let { soundManager.stop(it) }
                    compositionToStop?.stopComposition()
                }
            }.onFailure { e ->
                Logger.err("AudioPlayer $playerId: Error during pause: ${e.message}")
            }
        }
    }

    /**
     * Resume playback from a paused state.
     * Only works when in PAUSED state.
     */
    fun resume() {
        launchModCoroutine {
            // Capture what we need while holding lock briefly
            val soundInstanceToResume: SoundInstance?
            val compositionToResume: SoundEventComposition?

            stateMutex.withLock {
                if (playState != PlayState.PAUSED) return@launchModCoroutine

                val context = playbackContext
                if (context == null) {
                    Logger.err("AudioPlayer $playerId: Cannot resume, no playback context")
                    playState = PlayState.STOPPED
                    notifyStreamFailure()
                    return@launchModCoroutine
                }

                if (context.soundInstance == null || context.processingAudioStream == null) {
                    Logger.err("AudioPlayer $playerId: Cannot resume, resources were cleaned up (likely due to stream error)")
                    cleanupResourcesInternal()
                    notifyStreamFailure()
                    return@launchModCoroutine
                }

                // Verify FFmpeg is still running if this is a URL-based source
                val ffmpegExecutor = context.ffmpegExecutor
                if (ffmpegExecutor != null && !ffmpegExecutor.isRunning()) {
                    Logger.err("AudioPlayer $playerId: Cannot resume, FFmpeg process has terminated")
                    cleanupResourcesInternal()
                    notifyStreamFailure()
                    return@launchModCoroutine
                }

                soundInstanceToResume = context.soundInstance
                compositionToResume = context.soundComposition
                playState = PlayState.PLAYING
            }

            // Resume playback outside the lock to avoid blocking
            if (soundInstanceToResume != null && compositionToResume != null) {
                runCatching {
                    withClientContext {
                        soundManager.play(soundInstanceToResume)
                        compositionToResume.makeComposition(soundInstanceToResume)
                    }
                }.onFailure { e ->
                    Logger.err("AudioPlayer $playerId: Error during resume: ${e.message}")
                    // If resume failed, cleanup resources and notify
                    stateMutex.withLock {
                        cleanupResourcesInternal()
                        notifyStreamFailure()
                    }
                }
            }
        }
    }

    /**
     * Resolves the appropriate audio source for the given URL.
     */
    private fun resolveAudioSource(url: String): AudioSource? =
        when {
            url.contains("youtube.com") || url.contains("youtu.be") -> {
                YoutubeAudioSource(url)
            }

            else -> {
                YtdlpAudioSource(url)
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
        ModPackets.channel.sendToServer(AudioPlayerStreamEndPacket(playerId, true))
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
                // Stop composition (launches async on client thread)
                try {
                    context.soundComposition.stopComposition()
                } catch (e: Exception) {
                    Logger.err("AudioPlayer $playerId: Error stopping composition: ${e.message}")
                }

                // Stop the sound instance on client thread
                try {
                    context.soundInstance?.let { instance ->
                        // Launch on client thread to ensure thread safety
                        launchModCoroutine(MinecraftClientDispatcher) {
                            try {
                                soundManager.stop(instance)
                            } catch (e: Exception) {
                                Logger.err("AudioPlayer $playerId: Error stopping sound instance: ${e.message}")
                            }
                        }
                    }
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
