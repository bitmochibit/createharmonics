package me.mochibit.createharmonics.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.mochibit.createharmonics.audio.comp.SoundEventComposition
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.instance.StreamingSoundInstance
import me.mochibit.createharmonics.audio.process.FFmpegExecutor
import me.mochibit.createharmonics.audio.source.AudioSource
import me.mochibit.createharmonics.audio.source.HttpAudioSource
import me.mochibit.createharmonics.audio.source.YtdlpAudioSource
import me.mochibit.createharmonics.audio.stream.AudioEffectInputStream
import me.mochibit.createharmonics.foundation.async.modLaunch
import me.mochibit.createharmonics.foundation.async.withMainContext
import me.mochibit.createharmonics.foundation.debug
import me.mochibit.createharmonics.foundation.err
import me.mochibit.createharmonics.foundation.extension.ticks
import me.mochibit.createharmonics.foundation.warn
import me.mochibit.createharmonics.network.packet.AudioPlayerStreamEndPacket
import me.mochibit.createharmonics.network.packet.UpdateAudioNamePacket
import me.mochibit.createharmonics.registry.ModPackets
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import java.io.InputStream
import java.util.UUID

typealias StreamId = String
typealias StreamingSoundInstanceProvider = (streamId: me.mochibit.createharmonics.audio.StreamId, stream: InputStream) -> SoundInstance

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
    val soundInstanceProvider: me.mochibit.createharmonics.audio.StreamingSoundInstanceProvider,
    val playerId: String = UUID.randomUUID().toString(),
    val sampleRate: Int = 48000,
    val onEffectChainCreate: ((effectChain: me.mochibit.createharmonics.audio.effect.EffectChain) -> Unit)? = null,
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
        val effectChain: me.mochibit.createharmonics.audio.effect.EffectChain,
        val soundComposition: me.mochibit.createharmonics.audio.comp.SoundEventComposition,
        val offsetSeconds: Double,
        var ffmpegExecutor: me.mochibit.createharmonics.audio.process.FFmpegExecutor? = null,
        var processingAudioStream: me.mochibit.createharmonics.audio.stream.AudioEffectInputStream? = null,
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

            modLaunch(Dispatchers.IO) {
                try {
                    composition.stopComposition()
                } catch (e: Exception) {
                    "Error stopping composition: ${e.message}".err()
                }

                try {
                    executor?.destroy()
                } catch (e: Exception) {
                    "Error destroying FFmpeg: ${e.message}".err()
                }

                try {
                    stream?.close()
                } catch (e: Exception) {
                    "Error closing audio stream: ${e.message}".err()
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

    fun getCurrentEffectChain(): me.mochibit.createharmonics.audio.effect.EffectChain? = playbackContext?.effectChain

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
        effectChain: me.mochibit.createharmonics.audio.effect.EffectChain =
            _root_ide_package_.me.mochibit.createharmonics.audio.effect.EffectChain
                .empty(),
        soundEventComposition: me.mochibit.createharmonics.audio.comp.SoundEventComposition =
            _root_ide_package_.me.mochibit.createharmonics.audio.comp
                .SoundEventComposition(),
        offsetSeconds: Double = 0.0,
    ) {
        if (url.isBlank()) {
            "AudioPlayer $playerId: Cannot play empty URL".err()
            return
        }

        modLaunch(Dispatchers.IO) {
            val source = PlaybackSource.Url(url)
            val context: PlaybackContext

            stateMutex.withLock {
                // Check inside lock to prevent race conditions
                if (playState == PlayState.PLAYING || playState == PlayState.LOADING) {
                    val currentSource = playbackContext?.source
                    if (currentSource == source && playbackContext?.effectChain == effectChain) {
                        return@modLaunch
                    }
                }

                if (playState != PlayState.STOPPED) {
                    cleanupResourcesInternal()
                }

                playState = PlayState.LOADING
                context = PlaybackContext(source, effectChain, soundEventComposition, offsetSeconds)
                onEffectChainCreate?.invoke(effectChain)
                playbackContext = context
            }

            val initResult =
                runCatching {
                    initializeUrlPlayback(url, context)
                }

            stateMutex.withLock {
                if (playbackContext != context) {
                    "AudioPlayer $playerId: Context changed, cleaning up abandoned resources".debug()
                    context.cleanup()
                    return@modLaunch
                }

                initResult
                    .onSuccess {
                        playState = PlayState.PLAYING
                    }.onFailure { e ->
                        "AudioPlayer $playerId: Error during playback: ${e.message}".err()
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
        effectChain: me.mochibit.createharmonics.audio.effect.EffectChain = EffectChain.empty(),
        soundEventComposition: SoundEventComposition = SoundEventComposition(),
        offsetSeconds: Double = 0.0,
        sampleRateOverride: Int? = null,
    ) {
        modLaunch(Dispatchers.IO) {
            val context: PlaybackContext
            stateMutex.withLock {
                val source = PlaybackSource.Stream(inputStream, audioName)

                if (playState != PlayState.STOPPED) {
                    cleanupResourcesInternal()
                }

                playState = PlayState.LOADING
                context = PlaybackContext(source, effectChain, soundEventComposition, offsetSeconds)
                onEffectChainCreate?.invoke(effectChain)
                playbackContext = context
            }

            val initResult =
                runCatching {
                    initializeStreamPlayback(inputStream, audioName, context, sampleRateOverride)
                }

            stateMutex.withLock {
                // Verify we're still working with the same context
                if (playbackContext != context) {
                    "AudioPlayer $playerId: Context changed during stream initialization, aborting".debug()
                    return@modLaunch
                }

                initResult
                    .onSuccess {
                        playState = PlayState.PLAYING
                    }.onFailure { e ->
                        "AudioPlayer $playerId: Error during stream playback: ${e.message}".err()
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

//        if (!audioStream.awaitPreBuffering()) {
//            throw IllegalStateException("Pre-buffering timeout")
//        }

        if (playState != PlayState.LOADING) {
            throw IllegalStateException("Aborting playback")
        }

        startPlayback(audioStream, context)
    }

    /**
     * Invalidate the cached URL and retry playback once.
     * This is called when FFmpeg fails (e.g. expired extracted URL from yt-dlp).
     *
     * For direct HTTP URLs there is no cache to invalidate — the URL itself is the
     * problem (e.g. an expired CDN token). In that case we fail immediately with a
     * clear message rather than pointlessly retrying the same dead URL.
     */
    private suspend fun invalidateUrlCacheAndRetry(
        url: String,
        context: PlaybackContext,
    ) {
        // Direct audio URLs (CDN links, etc.) can't be refreshed — the URL is the source of truth.
        // Retrying would just hit the same expired/missing resource again.
        if (isDirectAudioUrl(url)) {
            "AudioPlayer $playerId: Direct audio URL is unreachable (expired or invalid): $url".err()
            resetStateInternal()
            notifyStreamFailure()
            return
        }

        // Prevent infinite retry loops
        if (context.hasRetried) {
            resetStateInternal()
            notifyStreamFailure()
            return
        }

        context.hasRetried = true

        // Invalidate the cache to force fresh URL extraction
        me.mochibit.createharmonics.audio.cache.AudioInfoCache
            .invalidate(url)

        // Small delay before retry
        delay(500)

        runCatching {
            initializeUrlPlayback(url, context)
        }.onSuccess {
            playState = PlayState.PLAYING
        }.onFailure { e ->
            "AudioPlayer $playerId: Retry failed: ${e.message}".err()
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
                "AudioPlayer $playerId: Adjusted skip to $bytesToSkip bytes for sample alignment".warn()
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
                        "AudioPlayer $playerId: Could only skip $totalSkipped of $bytesToSkip bytes (reached end of stream)".warn()
                        break
                    }

                    totalSkipped += bytesRead
                    remaining -= bytesRead
                }
            }
        }

        // Create effect stream directly from input (no FFmpeg)
        val audioStream = createAudioEffectInputStream(inputStream, context, sampleRateOverride)
        context.processingAudioStream = audioStream

//        if (!audioStream.awaitPreBuffering()) {
//            throw IllegalStateException("Pre-buffering timeout")
//        }

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

        withMainContext {
            context.soundComposition.makeComposition(soundInstance)
            soundManager.play(soundInstance)
        }
    }

    private fun handleStreamEnd() {
        modLaunch(Dispatchers.IO) {
            if (!stateMutex.tryLock()) {
                return@modLaunch
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
        if (playState != PlayState.PLAYING) return

        playbackContext?.soundInstance?.let { soundInstance ->
            modLaunch(Dispatchers.IO) {
                delay(1.ticks())
                try {
                    withMainContext {
                        soundManager.play(soundInstance)
                    }
                } catch (e: Exception) {
                    "AudioPlayer $playerId: Error restarting hung stream: ${e.message}".err()
                }
            }
        }
    }

    /**
     * Stop playback and release all resources.
     * Transitions to STOPPED state.
     */
    fun stop() {
        modLaunch(Dispatchers.IO) {
            // Capture sound instance while holding lock briefly
            val soundInstanceToStop: SoundInstance?
            stateMutex.withLock {
                if (playState == PlayState.STOPPED) return@modLaunch
                soundInstanceToStop = playbackContext?.soundInstance
                cleanupResourcesInternal()
            }

            // Stop sound outside the lock to avoid blocking
            if (soundInstanceToStop != null) {
                runCatching {
                    withMainContext {
                        soundManager.stop(soundInstanceToStop)
                    }
                }.onFailure { e ->
                    "AudioPlayer $playerId: Error stopping sound: ${e.message}".err()
                }
            }
        }
    }

    /**
     * Pause the current playback.
     * Only works when in PLAYING state. Can be resumed later.
     */
    fun pause() {
        modLaunch {
            // Capture what we need while holding lock briefly
            val soundInstanceToStop: SoundInstance?
            val compositionToStop: SoundEventComposition?
            stateMutex.withLock {
                if (playState != PlayState.PLAYING) return@modLaunch
                soundInstanceToStop = playbackContext?.soundInstance
                compositionToStop = playbackContext?.soundComposition
                playState = PlayState.PAUSED
            }

            // Stop sound and composition outside the lock to avoid blocking
            runCatching {
                withMainContext {
                    soundInstanceToStop?.let { soundManager.stop(it) }
                    compositionToStop?.stopComposition()
                }
            }.onFailure { e ->
                "AudioPlayer $playerId: Error during pause: ${e.message}".err()
            }
        }
    }

    /**
     * Resume playback from a paused state.
     * Only works when in PAUSED state.
     */
    fun resume() {
        modLaunch {
            // Capture what we need while holding lock briefly
            val soundInstanceToResume: SoundInstance?
            val compositionToResume: SoundEventComposition?

            stateMutex.withLock {
                if (playState != PlayState.PAUSED) return@modLaunch

                val context = playbackContext
                if (context == null) {
                    playState = PlayState.STOPPED
                    notifyStreamFailure()
                    return@modLaunch
                }

                if (context.soundInstance == null || context.processingAudioStream == null) {
                    cleanupResourcesInternal()
                    notifyStreamFailure()
                    return@modLaunch
                }

                // Verify FFmpeg is still running if this is a URL-based source
                val ffmpegExecutor = context.ffmpegExecutor
                if (ffmpegExecutor != null && !ffmpegExecutor.isRunning()) {
                    cleanupResourcesInternal()
                    notifyStreamFailure()
                    return@modLaunch
                }

                soundInstanceToResume = context.soundInstance
                compositionToResume = context.soundComposition
                playState = PlayState.PLAYING
            }

            // Resume playback outside the lock to avoid blocking
            if (soundInstanceToResume != null && compositionToResume != null) {
                runCatching {
                    withMainContext {
                        soundManager.play(soundInstanceToResume)
                        compositionToResume.makeComposition(soundInstanceToResume)
                    }
                }.onFailure { e ->
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
     *
     * Direct audio file URLs (e.g. CDN-hosted mp3/wav/m4a/etc.) are fed straight
     * to FFmpeg without going through yt-dlp, which significantly reduces startup
     * latency for self-hosted files.
     */
    private fun resolveAudioSource(url: String): AudioSource? =
        when {
            isDirectAudioUrl(url) -> {
                HttpAudioSource(url)
            }

            else -> {
                YtdlpAudioSource(url)
            }
        }

    /**
     * Returns true if the URL points directly to a known audio/video file format,
     * meaning it can be streamed by FFmpeg without yt-dlp resolution.
     *
     * Detection is done on the path portion only (before any query string / fragment)
     * so CDN URLs with tokens like `?token=abc` are handled correctly.
     */
    private fun isDirectAudioUrl(url: String): Boolean {
        val directAudioExtensions =
            setOf(
                "mp3",
                "mp4",
                "m4a",
                "m4b",
                "wav",
                "wave",
                "ogg",
                "oga",
                "opus",
                "flac",
                "aac",
                "webm",
                "wma",
                "aiff",
                "aif",
            )
        // Strip query params and fragment, grab the extension of the last path segment
        val path = url.substringBefore('?').substringBefore('#')
        val extension = path.substringAfterLast('.', "").lowercase()
        return extension in directAudioExtensions
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

        modLaunch {
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
                    "AudioPlayer $playerId: Error stopping composition: ${e.message}".err()
                }

                // Stop the sound instance on client thread
                try {
                    context.soundInstance?.let { instance ->
                        // Launch on client thread to ensure thread safety
                        modLaunch {
                            try {
                                soundManager.stop(instance)
                            } catch (e: Exception) {
                                "AudioPlayer $playerId: Error stopping sound instance: ${e.message}".err()
                            }
                        }
                    }
                } catch (e: Exception) {
                    "AudioPlayer $playerId: Error stopping sound: ${e.message}".err()
                }

                // Cleanup resources
                try {
                    context.cleanup()
                } catch (e: Exception) {
                    "AudioPlayer $playerId: Error during cleanup: ${e.message}".err()
                }
            }
        } catch (e: Exception) {
            "AudioPlayer $playerId: Error in stopSoundImmediately: ${e.message}".err()
        }
    }
}
