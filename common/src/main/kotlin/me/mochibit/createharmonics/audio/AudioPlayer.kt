package me.mochibit.createharmonics.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.mochibit.createharmonics.audio.comp.SoundEventComposition
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.instance.SampleRatedInstance
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
import me.mochibit.createharmonics.foundation.info
import me.mochibit.createharmonics.foundation.network.packet.AudioPlayerStreamEndPacket
import me.mochibit.createharmonics.foundation.network.packet.UpdateAudioNamePacket
import me.mochibit.createharmonics.foundation.registry.ModPackets
import me.mochibit.createharmonics.foundation.warn
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import java.io.InputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

typealias StreamId = String
typealias StreamingSoundInstanceProvider = (streamId: StreamId, stream: InputStream) -> SoundInstance

// ---------------------------------------------------------------------------
// Internal exceptions
// ---------------------------------------------------------------------------

/**
 * Thrown when an audio stream is fully consumed during the initial seek phase,
 * meaning the requested offset is past the natural end of the stream.
 * Treated as a normal (non-failure) stream end, not an error.
 */
private class StreamExhaustedException(
    message: String,
) : Exception(message)

// ---------------------------------------------------------------------------
// PlaybackClock – tracks elapsed playback time, pause-aware
// ---------------------------------------------------------------------------

/**
 * A monotonic clock that measures elapsed playback time.
 *
 * - [start] begins counting from an initial offset.
 * - [pause] freezes the reading.
 * - [resume] continues counting from the frozen position.
 * - [reset] brings everything back to zero / stopped.
 * - [syncTo] jumps to the supplied offset (used for external drift correction).
 *
 * **Not thread-safe.** All access must be guarded by the owning [AudioPlayer]'s
 * [AudioPlayer.stateMutex].
 */
private class PlaybackClock {
    /** Wall-clock timestamp (ms) when playback was last (re-)started. */
    private var startWallMs: Long = 0L

    /** Milliseconds already elapsed before the current [startWallMs] anchor. */
    private var baseMs: Long = 0L

    /**
     * When non-null the clock is frozen at the given elapsed-ms value.
     * Null means the clock is either ticking or has never been started.
     */
    private var frozenMs: Long? = null

    // -----------------------------------------------------------------------
    // Reads
    // -----------------------------------------------------------------------

    /**
     * Current elapsed playback time in seconds.
     * Returns `0.0` if the clock has never been started.
     */
    val elapsedSeconds: Double
        get() =
            when {
                startWallMs == 0L -> 0.0
                frozenMs != null -> frozenMs!! / 1000.0
                else -> (baseMs + (System.currentTimeMillis() - startWallMs)) / 1000.0
            }

    // -----------------------------------------------------------------------
    // Mutations
    // -----------------------------------------------------------------------

    /** Start (or restart) the clock from [offsetSeconds]. */
    fun start(offsetSeconds: Double = 0.0) {
        baseMs = (offsetSeconds * 1000.0).toLong()
        startWallMs = System.currentTimeMillis()
        frozenMs = null
    }

    /** Freeze the clock at the current reading. No-op if already frozen or never started. */
    fun pause() {
        if (frozenMs == null && startWallMs != 0L) {
            frozenMs = baseMs + (System.currentTimeMillis() - startWallMs)
        }
    }

    /** Unfreeze and continue ticking from the frozen position. No-op if not frozen. */
    fun resume() {
        val frozen = frozenMs ?: return
        baseMs = frozen
        startWallMs = System.currentTimeMillis()
        frozenMs = null
    }

    /** Reset to zero and stop ticking. */
    fun reset() {
        startWallMs = 0L
        baseMs = 0L
        frozenMs = null
    }

    /**
     * Externally correct the position to [offsetSeconds] without altering the
     * running / paused / stopped status. Ignored if the clock was never started.
     */
    fun syncTo(offsetSeconds: Double) {
        if (startWallMs == 0L) return
        val newBaseMs = (offsetSeconds * 1000.0).toLong()
        if (frozenMs != null) {
            frozenMs = newBaseMs
        } else {
            baseMs = newBaseMs
            startWallMs = System.currentTimeMillis()
        }
    }
}

// ---------------------------------------------------------------------------
// AudioPlayer
// ---------------------------------------------------------------------------

/**
 * Responsible for playing custom sound sources to the Minecraft engine.
 *
 * Supported features:
 * - Multiple audio sources (YouTube, HTTP, direct streams via [playFromStream])
 * - Audio effect chains applied on the PCM pipeline
 * - Full playback control: [play], [playFromStream], [pause], [resume], [stop], [dispose]
 * - **Internal playback clock** – [currentPositionSeconds] returns the running
 *   elapsed time that automatically pauses/freezes with the player state.
 * - **External position sync** – [syncPosition] lets authoritative callers (e.g.
 *   `RecordPlayerBehaviour`) correct clock drift without triggering a full stream
 *   restart unless the deviation is large (> [SYNC_DRIFT_THRESHOLD_SECONDS]).
 * - Automatic stream cleanup and fully thread-safe state management via [stateMutex].
 *
 * @param soundInstanceProvider Factory that wraps a raw [InputStream] into a Minecraft [SoundInstance].
 * @param playerId              Unique identifier for this player instance.
 * @param sampleRate            PCM sample rate of the output stream (default 48 kHz).
 * @param onEffectChainCreate   Optional callback invoked once for each new [EffectChain].
 */
class AudioPlayer(
    val soundInstanceProvider: StreamingSoundInstanceProvider,
    val playerId: String = UUID.randomUUID().toString(),
    val sampleRate: Int = 48_000,
    val onEffectChainCreate: ((effectChain: EffectChain) -> Unit)? = null,
) {
    // -----------------------------------------------------------------------
    // Companion – constants & pure helpers
    // -----------------------------------------------------------------------

    companion object {
        /**
         * Maximum allowable drift (seconds) between the internal clock and an
         * externally supplied position before [syncPosition] triggers a full stream
         * restart.  Below this threshold only the clock is nudged.
         */
        const val SYNC_DRIFT_THRESHOLD_SECONDS: Double = 3.0

        private val DIRECT_AUDIO_EXTENSIONS =
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

        private fun isDirectAudioUrl(url: String): Boolean {
            val path = url.substringBefore('?').substringBefore('#')
            return path.substringAfterLast('.', "").lowercase() in DIRECT_AUDIO_EXTENSIONS
        }
    }

    // -----------------------------------------------------------------------
    // Public types
    // -----------------------------------------------------------------------

    /** Observable playback state machine. */
    enum class PlayState {
        /** No audio is playing; all resources are released. */
        STOPPED,

        /** Audio pipeline is being initialised (FFmpeg / source resolution). */
        LOADING,

        /** Audio is actively playing. */
        PLAYING,

        /** Audio is paused; can be resumed without restarting the stream. */
        PAUSED,
    }

    // -----------------------------------------------------------------------
    // Private types
    // -----------------------------------------------------------------------

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
     * Holds every resource that belongs to one logical playback session.
     * [cleanup] is idempotent and safe to call more than once from any thread.
     */
    private inner class PlaybackContext(
        val source: PlaybackSource,
        val effectChain: EffectChain,
        val soundComposition: SoundEventComposition,
        val offsetSeconds: Double,
    ) {
        var ffmpegExecutor: FFmpegExecutor? = null
        var processingAudioStream: AudioEffectInputStream? = null
        var soundInstance: SoundInstance? = null
        var hasRetried: Boolean = false

        fun cleanup() {
            // Snapshot and null-out before async teardown to prevent double-close
            val composition = soundComposition
            val executor = ffmpegExecutor
            val stream = processingAudioStream

            ffmpegExecutor = null
            processingAudioStream = null
            soundInstance = null

            modLaunch(Dispatchers.IO) {
                runCatching { composition.stopComposition() }
                    .onFailure { "AudioPlayer $playerId: Error stopping composition: ${it.message}".err() }
                runCatching { executor?.destroy() }
                    .onFailure { "AudioPlayer $playerId: Error destroying FFmpeg: ${it.message}".err() }
                runCatching { stream?.close() }
                    .onFailure { "AudioPlayer $playerId: Error closing audio stream: ${it.message}".err() }
            }
        }
    }

    // -----------------------------------------------------------------------
    // State  (all mutable state lives here; guard with stateMutex)
    // -----------------------------------------------------------------------

    /** Guards all mutable playback state and the [clock]. */
    private val stateMutex = Mutex()

    @Volatile private var playState: PlayState = PlayState.STOPPED

    @Volatile private var playbackContext: PlaybackContext? = null

    /** Pause-aware monotonic playback clock. Access only under [stateMutex]. */
    private val clock = PlaybackClock()

    /**
     * Wall-clock timestamp (ms) of the last sync-triggered stream restart.
     * Used to enforce a cooldown so that rapid successive [syncPosition] calls
     * do not spawn multiple overlapping restart coroutines.
     * Access only under [stateMutex].
     */
    private var lastSyncRestartMs: Long = 0L

    // -----------------------------------------------------------------------
    // Public API – observable state
    // -----------------------------------------------------------------------

    val state: PlayState get() = playState

    /**
     * Current playback position in seconds since the start of the track.
     *
     * - Advances while [PlayState.PLAYING].
     * - Frozen while [PlayState.PAUSED].
     * - Returns `0.0` while [PlayState.STOPPED] or [PlayState.LOADING].
     *
     * Lock-free read; suitable for calling on any thread.
     */
    val currentPositionSeconds: Double get() = clock.elapsedSeconds

    fun getCurrentEffectChain(): EffectChain? = playbackContext?.effectChain

    // -----------------------------------------------------------------------
    // Public API – playback control
    // -----------------------------------------------------------------------

    /**
     * Start playing audio from [url].
     *
     * If already playing the same source with the same effect chain, this is a no-op.
     * Otherwise, the current session is stopped and a new one is started from [offsetSeconds].
     */
    fun play(
        url: String,
        effectChain: EffectChain = EffectChain.empty(),
        soundEventComposition: SoundEventComposition = SoundEventComposition(),
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
                // Idempotency guard — same source + chain already active
                if ((playState == PlayState.PLAYING || playState == PlayState.LOADING) &&
                    playbackContext?.source == source &&
                    playbackContext?.effectChain == effectChain
                ) {
                    return@modLaunch
                }

                if (playState != PlayState.STOPPED) cleanupInternal()

                playState = PlayState.LOADING
                context =
                    PlaybackContext(source, effectChain, soundEventComposition, offsetSeconds)
                        .also { onEffectChainCreate?.invoke(effectChain) }
                playbackContext = context
            }

            val result = runCatching { initializeUrlPlayback(url, context) }

            stateMutex.withLock {
                // Context may have been superseded by a concurrent stop/play
                if (playbackContext !== context) {
                    context.cleanup()
                    return@modLaunch
                }
                result
                    .onSuccess { effectiveOffset -> transitionToPlaying(effectiveOffset) }
                    .onFailure { e ->
                        "AudioPlayer $playerId: Error during playback: ${e.message}".err()
                        invalidateUrlCacheAndRetry(url, context)
                    }
            }
        }
    }

    /**
     * Start playing audio from a raw PCM [inputStream], bypassing FFmpeg.
     * The stream is fed directly through the effect chain.
     *
     * @param offsetSeconds Bytes corresponding to this many seconds will be skipped before playback starts.
     */
    fun playFromStream(
        inputStream: InputStream,
        audioName: String = "Stream",
        effectChain: EffectChain = EffectChain.empty(),
        soundEventComposition: SoundEventComposition = SoundEventComposition(),
        offsetSeconds: Double = 0.0,
        sampleRateOverride: Int? = null,
    ) {
        modLaunch(Dispatchers.IO) {
            val context: PlaybackContext
            stateMutex.withLock {
                if (playState != PlayState.STOPPED) cleanupInternal()
                playState = PlayState.LOADING
                context =
                    PlaybackContext(
                        PlaybackSource.Stream(inputStream, audioName),
                        effectChain,
                        soundEventComposition,
                        offsetSeconds,
                    ).also { onEffectChainCreate?.invoke(effectChain) }
                playbackContext = context
            }

            val result =
                runCatching {
                    initializeStreamPlayback(inputStream, audioName, context, sampleRateOverride)
                }

            stateMutex.withLock {
                if (playbackContext !== context) return@modLaunch
                result
                    .onSuccess { transitionToPlaying(offsetSeconds) }
                    .onFailure { e ->
                        "AudioPlayer $playerId: Error during stream playback: ${e.message}".err()
                        cleanupInternal()
                        if (e is StreamExhaustedException) notifyStreamEnd() else notifyStreamFailure()
                    }
            }
        }
    }

    /**
     * Stop playback and release all resources. No-op when already [PlayState.STOPPED].
     */
    fun stop() {
        modLaunch(Dispatchers.IO) {
            val instanceToStop: SoundInstance?
            stateMutex.withLock {
                if (playState == PlayState.STOPPED) return@modLaunch
                instanceToStop = playbackContext?.soundInstance
                cleanupInternal()
            }
            instanceToStop?.stopInEngine()
        }
    }

    /**
     * Pause playback. Only has an effect when [PlayState.PLAYING].
     */
    fun pause() {
        modLaunch {
            val instanceToStop: SoundInstance?
            val compositionToStop: SoundEventComposition?
            stateMutex.withLock {
                if (playState != PlayState.PLAYING) return@modLaunch
                instanceToStop = playbackContext?.soundInstance
                compositionToStop = playbackContext?.soundComposition
                playState = PlayState.PAUSED
                clock.pause()
            }
            runCatching {
                withMainContext {
                    instanceToStop?.let { soundManager.stop(it) }
                    compositionToStop?.stopComposition()
                }
            }.onFailure { "AudioPlayer $playerId: Error during pause: ${it.message}".err() }
        }
    }

    /**
     * Resume from [PlayState.PAUSED].
     * Transitions back to [PlayState.PLAYING] and re-submits the existing sound instance
     * to the Minecraft sound engine.
     */
    fun resume() {
        modLaunch {
            val instanceToResume: SoundInstance
            val compositionToResume: SoundEventComposition

            stateMutex.withLock {
                if (playState != PlayState.PAUSED) return@modLaunch

                val ctx = playbackContext
                when {
                    ctx == null -> {
                        "AudioPlayer $playerId: Cannot resume – no playback context".err()
                        cleanupInternal()
                        notifyStreamFailure()
                        return@modLaunch
                    }

                    ctx.soundInstance == null || ctx.processingAudioStream == null -> {
                        "AudioPlayer $playerId: Cannot resume – resources were cleaned up".err()
                        cleanupInternal()
                        notifyStreamFailure()
                        return@modLaunch
                    }

                    ctx.ffmpegExecutor?.isRunning() == false -> {
                        "AudioPlayer $playerId: Cannot resume – FFmpeg has terminated".err()
                        cleanupInternal()
                        notifyStreamFailure()
                        return@modLaunch
                    }
                }

                // Safe: all null-checks passed above
                instanceToResume = checkNotNull(ctx.soundInstance)
                compositionToResume = ctx.soundComposition
                playState = PlayState.PLAYING
                clock.resume()
            }

            runCatching {
                withMainContext {
                    soundManager.play(instanceToResume)
                    compositionToResume.makeComposition(instanceToResume)
                }
            }.onFailure { e ->
                "AudioPlayer $playerId: Error during resume: ${e.message}".err()
                stateMutex.withLock {
                    cleanupInternal()
                    notifyStreamFailure()
                }
            }
        }
    }

    /**
     * Correct the playback position to [targetPositionSeconds] as reported by an
     * authoritative external source (e.g. `RecordPlayerBehaviour`).
     *
     * - Ignored while [PlayState.STOPPED] or [PlayState.LOADING].
     * - If `|clock − target| ≤ [SYNC_DRIFT_THRESHOLD_SECONDS]`, only the clock is
     *   nudged — the audio stream keeps running undisturbed.
     * - If the deviation exceeds the threshold, the stream is fully restarted from
     *   [targetPositionSeconds] to realign audio with the game world.
     *
     * Intentionally does **not** restart the stream on every call; this keeps
     * network and CPU overhead low for the common small-drift case.
     */
    fun syncPosition(targetPositionSeconds: Double) {
        modLaunch(Dispatchers.IO) {
            // Snapshot of everything we need to restart, captured under the lock.
            // Declared outside the lambda so the data class isn't a local class inside a coroutine.
            var snapshotUrl: String? = null
            var snapshotEffectChain: EffectChain? = null
            var snapshotComposition: SoundEventComposition? = null
            var needsStreamRestart = false
            var isStream = false

            stateMutex.withLock {
                // Ignore while no audio is active or while a previous restart is still loading
                if (playState == PlayState.STOPPED || playState == PlayState.LOADING) return@modLaunch

                val drift = targetPositionSeconds - clock.elapsedSeconds
                if (abs(drift) <= SYNC_DRIFT_THRESHOLD_SECONDS) {
                    // Small drift — just nudge the clock, keep stream alive
                    clock.syncTo(targetPositionSeconds)
                    "AudioPlayer $playerId: clock nudged to %.2fs (drift %.2fs)"
                        .format(targetPositionSeconds, drift)
                        .debug()
                    return@modLaunch
                }

                // Large drift — but only restart if we're past the cooldown window.
                // This prevents rapid-fire restart loops where each restart briefly
                // produces a new drift reading before the clock stabilises.
                val now = System.currentTimeMillis()
                val cooldownMs = (SYNC_DRIFT_THRESHOLD_SECONDS * 1000).toLong() + 2_000L
                if (now - lastSyncRestartMs < cooldownMs) {
                    // Still inside the cooldown — just nudge the clock instead
                    clock.syncTo(targetPositionSeconds)
                    "AudioPlayer $playerId: sync cooldown active, nudging clock to %.2fs (drift %.2fs)"
                        .format(targetPositionSeconds, drift)
                        .debug()
                    return@modLaunch
                }

                "AudioPlayer $playerId: drift %.2fs exceeds threshold – restarting from %.2fs"
                    .format(drift, targetPositionSeconds)
                    .info()

                lastSyncRestartMs = now

                val ctx = playbackContext
                when (val src = ctx?.source) {
                    is PlaybackSource.Url -> {
                        snapshotUrl = src.url
                        snapshotEffectChain = ctx.effectChain
                        snapshotComposition = ctx.soundComposition
                        needsStreamRestart = true
                    }

                    is PlaybackSource.Stream -> {
                        isStream = true
                    }

                    null -> {}
                }
            }

            // Restart outside the lock to avoid dead-locking play()
            when {
                needsStreamRestart -> {
                    forceRestartFromOffset(
                        snapshotUrl!!,
                        snapshotEffectChain!!,
                        snapshotComposition!!,
                        targetPositionSeconds,
                    )
                }

                isStream -> {
                    // Raw streams can't be seeked — just correct the clock
                    stateMutex.withLock { clock.syncTo(targetPositionSeconds) }
                }
            }
        }
    }

    /**
     * Forces a stream restart from [offsetSeconds], bypassing the idempotency guard in [play].
     * Used by [syncPosition] to ensure the restart actually happens even when the source URL
     * and effect chain are identical to the currently-playing session.
     */
    private suspend fun forceRestartFromOffset(
        url: String,
        effectChain: EffectChain,
        soundEventComposition: SoundEventComposition,
        offsetSeconds: Double,
    ) {
        val source = PlaybackSource.Url(url)
        val context: PlaybackContext

        stateMutex.withLock {
            // If another restart already started while we were waiting for the lock, bail out
            if (playState == PlayState.LOADING) {
                "AudioPlayer $playerId: forceRestartFromOffset – already loading, skipping".debug()
                return
            }

            if (playState != PlayState.STOPPED) cleanupInternal()

            playState = PlayState.LOADING
            context =
                PlaybackContext(source, effectChain, soundEventComposition, offsetSeconds)
                    .also { onEffectChainCreate?.invoke(effectChain) }
            playbackContext = context
        }

        val result = runCatching { initializeUrlPlayback(url, context) }

        stateMutex.withLock {
            if (playbackContext !== context) {
                context.cleanup()
                return
            }
            result
                .onSuccess { transitionToPlaying(offsetSeconds) }
                .onFailure { e ->
                    "AudioPlayer $playerId: Error during sync-restart: ${e.message}".err()
                    invalidateUrlCacheAndRetry(url, context)
                }
        }
    }

    /**
     * Dispose of this player and release all resources unconditionally.
     * The player must not be used after this call.
     */
    fun dispose() {
        stopSoundImmediately()
        modLaunch { stateMutex.withLock { cleanupInternal() } }
    }

    /**
     * Synchronously cancel the sound without waiting for coroutines.
     * Safe to call from any thread (e.g. during level/world unload).
     */
    fun stopSoundImmediately() {
        val ctx = playbackContext ?: return
        runCatching { ctx.soundComposition.stopComposition() }
            .onFailure { "AudioPlayer $playerId: Error stopping composition: ${it.message}".err() }
        ctx.soundInstance?.let { instance ->
            modLaunch {
                runCatching { withMainContext { soundManager.stop(instance) } }
                    .onFailure { "AudioPlayer $playerId: Error stopping sound instance: ${it.message}".err() }
            }
        }
        runCatching { ctx.cleanup() }
            .onFailure { "AudioPlayer $playerId: Error during immediate cleanup: ${it.message}".err() }
    }

    // -----------------------------------------------------------------------
    // Stream initialisation
    // -----------------------------------------------------------------------

    /**
     * Initialises URL-based playback and returns the effective start offset in seconds
     * (which may differ from [PlaybackContext.offsetSeconds] when looping wrap-around applies).
     */
    private suspend fun initializeUrlPlayback(
        url: String,
        context: PlaybackContext,
    ): Double {
        val audioSource =
            resolveAudioSource(url)
                ?: throw IllegalArgumentException("Unsupported audio source for URL: $url")

        audioSource
            .getAudioName()
            .takeIf { it != "Unknown" }
            ?.let { ModPackets.sendToServer(UpdateAudioNamePacket(playerId, it)) }

        val duration = audioSource.getDurationSeconds()
        val effectiveOffset =
            when {
                duration > 0 && context.offsetSeconds >= duration -> {
                    (context.offsetSeconds % duration).also {
                        "AudioPlayer $playerId: offset ${context.offsetSeconds}s wrapped to ${it}s (duration ${duration}s)".info()
                    }
                }

                else -> {
                    context.offsetSeconds
                }
            }

        val ffmpegExecutor = FFmpegExecutor().also { context.ffmpegExecutor = it }
        val effectiveUrl = audioSource.resolveAudioUrl()

        if (!ffmpegExecutor.createStream(effectiveUrl, sampleRate, effectiveOffset, audioSource.getHttpHeaders())) {
            throw IllegalStateException("FFmpeg stream initialisation failed")
        }

        val rawStream =
            ffmpegExecutor.inputStream
                ?: throw IllegalStateException("FFmpeg input stream is null after successful createStream")

        context.processingAudioStream = createAudioEffectInputStream(rawStream, context)

        check(playState == PlayState.LOADING) { "Aborting playback – state changed during initialisation" }
        startPlayback(context.processingAudioStream!!, context)

        return effectiveOffset
    }

    private suspend fun initializeStreamPlayback(
        inputStream: InputStream,
        audioName: String,
        context: PlaybackContext,
        sampleRateOverride: Int?,
    ) {
        if (audioName.isNotBlank() && audioName != "Unknown") {
            ModPackets.sendToServer(UpdateAudioNamePacket(playerId, audioName))
        }

        if (context.offsetSeconds > 0.0) {
            skipStreamToOffset(inputStream, context.offsetSeconds, sampleRateOverride ?: sampleRate)
        }

        context.processingAudioStream = createAudioEffectInputStream(inputStream, context, sampleRateOverride)

        check(playState == PlayState.LOADING) { "Aborting playback – state changed during initialisation" }
        startPlayback(context.processingAudioStream!!, context, sampleRateOverride)
    }

    /**
     * Skips bytes in [inputStream] to reach [offsetSeconds].
     * @throws [StreamExhaustedException] if the stream ends before the target offset.
     */
    private suspend fun skipStreamToOffset(
        inputStream: InputStream,
        offsetSeconds: Double,
        effectiveSampleRate: Int,
    ) {
        val bytesPerSecond = effectiveSampleRate * 2L // 16-bit mono → 2 bytes/sample
        var bytesToSkip = (offsetSeconds * bytesPerSecond).toLong()
        if (bytesToSkip % 2 != 0L) {
            bytesToSkip += 1
            "AudioPlayer $playerId: Adjusted skip to $bytesToSkip bytes for sample alignment".warn()
        }

        val buf = ByteArray(8192)
        var totalSkipped = 0L
        var remaining = bytesToSkip

        withContext(Dispatchers.IO) {
            while (remaining > 0) {
                val toRead = minOf(remaining, buf.size.toLong()).toInt()
                val read = inputStream.read(buf, 0, toRead)
                if (read <= 0) {
                    "AudioPlayer $playerId: Skipped only $totalSkipped/$bytesToSkip bytes – offset past stream end".warn()
                    throw StreamExhaustedException(
                        "Stream exhausted during seek: offset ${offsetSeconds}s is past the end of the stream",
                    )
                }
                totalSkipped += read
                remaining -= read
            }
        }
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
            soundInstanceProvider(playerId, audioStream).also { instance ->
                if (instance is SampleRatedInstance) {
                    instance.sampleRate = sampleRateOverride ?: sampleRate
                }
                context.soundInstance = instance
            }

        withMainContext {
            context.soundComposition.makeComposition(soundInstance)
            soundManager.play(soundInstance)
        }
    }

    // -----------------------------------------------------------------------
    // Stream event handlers
    // -----------------------------------------------------------------------

    private fun handleStreamEnd() {
        modLaunch(Dispatchers.IO) {
            if (!stateMutex.tryLock()) {
                "AudioPlayer $playerId: Stream end – mutex busy, ignoring duplicate event".debug()
                return@modLaunch
            }
            try {
                when (playState) {
                    PlayState.PLAYING -> {
                        cleanupInternal()
                        notifyStreamEnd()
                    }

                    PlayState.PAUSED -> {
                        cleanupInternal()
                        notifyStreamFailure()
                    }

                    else -> {} // LOADING / STOPPED – nothing to do
                }
            } finally {
                stateMutex.unlock()
            }
        }
    }

    private fun handleStreamHang() {
        if (playState != PlayState.PLAYING) return
        val instance = playbackContext?.soundInstance ?: return
        modLaunch(Dispatchers.IO) {
            delay(1.ticks())
            runCatching { withMainContext { soundManager.play(instance) } }
                .onFailure { "AudioPlayer $playerId: Error restarting hung stream: ${it.message}".err() }
        }
    }

    // -----------------------------------------------------------------------
    // URL retry / cache invalidation
    // -----------------------------------------------------------------------

    /**
     * Called under [stateMutex] after a URL playback failure.
     * For direct URLs the failure is final; for resolved URLs one retry is attempted
     * after invalidating the audio info cache.
     */
    private suspend fun invalidateUrlCacheAndRetry(
        url: String,
        context: PlaybackContext,
    ) {
        if (isDirectAudioUrl(url)) {
            "AudioPlayer $playerId: Direct audio URL unreachable: $url".err()
            cleanupInternal()
            notifyStreamFailure()
            return
        }
        if (context.hasRetried) {
            "AudioPlayer $playerId: Already retried once – giving up".err()
            cleanupInternal()
            notifyStreamFailure()
            return
        }

        context.hasRetried = true
        me.mochibit.createharmonics.audio.cache.AudioInfoCache
            .invalidate(url)
        delay(500.milliseconds)
        "AudioPlayer $playerId: Retrying with fresh URL…".info()

        runCatching { initializeUrlPlayback(url, context) }
            .onSuccess {
                playState = PlayState.PLAYING
                "AudioPlayer $playerId: Retry successful".info()
            }.onFailure { e ->
                "AudioPlayer $playerId: Retry failed: ${e.message}".err()
                cleanupInternal()
                notifyStreamFailure()
            }
    }

    // -----------------------------------------------------------------------
    // Source resolution
    // -----------------------------------------------------------------------

    private fun resolveAudioSource(url: String): AudioSource? = if (isDirectAudioUrl(url)) HttpAudioSource(url) else YtdlpAudioSource(url)

    // -----------------------------------------------------------------------
    // Internal state helpers  (must always be called under stateMutex)
    // -----------------------------------------------------------------------

    /**
     * Transitions to [PlayState.PLAYING] and starts the internal clock from [offsetSeconds].
     * **Requires [stateMutex].**
     */
    private fun transitionToPlaying(offsetSeconds: Double) {
        playState = PlayState.PLAYING
        clock.start(offsetSeconds)
        // Stamp the start time so syncPosition won't immediately restart a freshly-started stream
        // (FFmpeg has startup latency that would otherwise look like a large drift).
        lastSyncRestartMs = System.currentTimeMillis()
    }

    /**
     * Releases all resources, resets the clock, and transitions to [PlayState.STOPPED].
     * **Requires [stateMutex].**
     */
    private fun cleanupInternal() {
        playbackContext?.cleanup()
        playbackContext = null
        playState = PlayState.STOPPED
        clock.reset()
        lastSyncRestartMs = 0L
    }

    // -----------------------------------------------------------------------
    // Notifications
    // -----------------------------------------------------------------------

    private fun notifyStreamEnd() = ModPackets.sendToServer(AudioPlayerStreamEndPacket(playerId))

    private fun notifyStreamFailure() = ModPackets.sendToServer(AudioPlayerStreamEndPacket(playerId, true))

    // -----------------------------------------------------------------------
    // Minecraft sound engine helpers
    // -----------------------------------------------------------------------

    private val soundManager get() = Minecraft.getInstance().soundManager

    /** Stops [this] sound instance in the Minecraft sound engine, suppressing all errors. */
    private suspend fun SoundInstance.stopInEngine() {
        runCatching { withMainContext { soundManager.stop(this@stopInEngine) } }
            .onFailure { "AudioPlayer $playerId: Error stopping sound instance: ${it.message}".err() }
    }
}
