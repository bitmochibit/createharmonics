package me.mochibit.createharmonics.audio.player

import kotlinx.coroutines.*
import me.mochibit.createharmonics.audio.comp.SoundEventComposition
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.stream.AudioEffectInputStream
import me.mochibit.createharmonics.audio.utils.pause
import me.mochibit.createharmonics.audio.utils.unpause
import me.mochibit.createharmonics.foundation.async.ClientCoroutineScope
import me.mochibit.createharmonics.foundation.async.withMainContext
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

typealias SoundInstanceFactory = AudioPlayer.(stream: java.io.InputStream) -> SoundInstance


class AudioPlayer(
    val playerId: String,
    private val soundInstanceFactory: SoundInstanceFactory,
) : PlaybackActions {
    private val playerScope =
        CoroutineScope(
            ClientCoroutineScope.coroutineContext + SupervisorJob(ClientCoroutineScope.coroutineContext[Job]),
        )

    @Volatile
    private var currentAudioRequest: AudioRequest? = null

    @Volatile
    private var currentAudioEffectInputStream: AudioEffectInputStream? = null

    @Volatile
    private var currentSoundInstance: SoundInstance? = null

    @Volatile
    private var startPlaybackJob: Job? = null

    private val loadingGeneration = AtomicInteger(0)

    val playerTerminated = AtomicBoolean(false)
    val isSeekingDisabled = AtomicBoolean(false)

    @Volatile
    var sharedTimeline: PlaytimeClock? = null

    private val listening = AtomicBoolean(false)

    @Volatile
    var spatialContext: AudioSpatialContext? = null

    @Volatile
    var spatialContextKey: Any? = null

    @Volatile
    var fallbackProvider: (() -> AudioRequest?)? = null

    private val consecutiveFailures = AtomicInteger(0)

    val effectChain = EffectChain()
    val soundEventComposition = SoundEventComposition(soundEffectChain = effectChain)
    val localClock = PlaytimeClock()

    var spatial = SpatialAudioController()
        private set
    private val notifier = AudioPlayerNotifier(playerId)
    private val streamLoader = StreamLoader(effectChain, soundInstanceFactory, this)
    private val stateMachine = PlaybackStateMachine(playerScope, this)
    private val retryScheduler =
        RetryScheduler(scope = playerScope, onRetry = { stateMachine.send(PlayerIntent.Play(0.0)) })
    private val watchdog =
        PlaybackWatchdog(
            scope = playerScope,
            isPlaying = { state.value == PlayerState.PLAYING },
            currentInstance = { currentSoundInstance },
            isActiveInSoundManager = { instance -> soundManager.isActive(instance) },
            onHang = { stateMachine.send(PlayerIntent.AudioHanged) },
        )

    val state get() = stateMachine.state

    private val soundManager get() = Minecraft.getInstance().soundManager

    val masterVolumeInterpolator get() = spatial.masterVolume
    val masterPitchInterpolator get() = spatial.masterPitch
    val masterRadiusInterpolator get() = spatial.masterRadius

    init {
        stateMachine.start()
        watchdog.start()
    }

    fun play(initialPosition: Double = 0.0) = stateMachine.send(PlayerIntent.Play(initialPosition))

    fun pause() = stateMachine.send(PlayerIntent.Pause)

    fun stop() = stateMachine.send(PlayerIntent.Stop)

    fun seek(position: Double) = stateMachine.send(PlayerIntent.Seek(position))

    fun request(req: AudioRequest) = stateMachine.send(PlayerIntent.NewRequest(req))


    fun tick() {
        localClock.tick()
        spatial.tick(this)
    }

    fun close() {
        playerTerminated.set(true)
        if (listening.compareAndSet(true, false)) notifier.listenerLeft()
        retryScheduler.cancelPending()
        watchdog.stop()
        stateMachine.shutdown()
    }

    internal fun onStreamEnd() = stateMachine.send(PlayerIntent.AudioFinished)

    internal fun onStreamHang() = stateMachine.send(PlayerIntent.AudioHanged)

    override fun hasActiveSoundInstance(): Boolean = currentSoundInstance != null

    override fun isSeekingDisabled(): Boolean = isSeekingDisabled.get()

    override fun currentPlaytime(): Double =
        sharedTimeline?.currentPlaytime ?: localClock.currentPlaytime

    override fun isSameSourceAsCurrentRequest(request: AudioRequest): Boolean =
        currentAudioRequest?.isSameSource(request) == true

    override fun isValidGeneration(streamGeneration: Int): Boolean = streamGeneration == loadingGeneration.get()

    override fun setCurrentRequest(request: AudioRequest) {
        currentAudioRequest = request
        consecutiveFailures.set(0)
    }

    override fun resetRetrySchedule() = retryScheduler.reset()

    override fun cancelPendingRetry() = retryScheduler.cancelPending()

    override suspend fun cancelLoading() {
        startPlaybackJob?.cancelAndJoin()
    }

    override suspend fun startPlayback(position: Double) {
        val request = currentAudioRequest ?: return

        startPlaybackJob?.cancelAndJoin()
        val generation = loadingGeneration.incrementAndGet()
        stateMachine.transition(PlayerState.LOADING)

        val resolvePosition = { sharedTimeline?.takeIf { it.isPlaying }?.currentPlaytime ?: position }

        startPlaybackJob =
            playerScope.launch(Dispatchers.IO) {
                when (val result = streamLoader.load(request, resolvePosition)) {
                    is StreamLoadResult.Ready -> {
                        if (!isActive) {
                            result.stream.close()
                            return@launch
                        }
                        stateMachine.send(
                            PlayerIntent.StreamReady(
                                result.stream,
                                result.soundInstance,
                                result.audioInfo,
                                result.startedAt,
                                generation,
                            ),
                        )
                    }

                    StreamLoadResult.Finished -> stateMachine.send(PlayerIntent.AudioFinished)

                    is StreamLoadResult.Failed -> {
                        if (!isActive) return@launch

                        val failures = consecutiveFailures.incrementAndGet()
                        val threshold = if (result.permanent) 1 else 2
                        val fallback =
                            if (failures >= threshold) fallbackProvider?.invoke() else null

                        if (fallback != null) {
                            fallbackProvider = null
                            consecutiveFailures.set(0)
                            currentAudioRequest = fallback
                            stateMachine.send(PlayerIntent.StreamFailed(shouldDisableSeek = false, shouldRetry = false))
                            stateMachine.send(PlayerIntent.Play(0.0))
                        } else {
                            stateMachine.send(PlayerIntent.StreamFailed(result.shouldDisableSeek, result.shouldRetry))
                        }
                    }
                }
            }
    }

    override suspend fun pausePlayback() {
        val capturedInstance = currentSoundInstance ?: return failPlayback()

        localClock.pause()
        stateMachine.transition(PlayerState.PAUSED)
        withContext(NonCancellable) {
            withMainContext {
                if (!capturedInstance.pause()) soundManager.stop(capturedInstance)
            }
        }
    }

    override suspend fun resumePlayback() {
        val capturedInstance = currentSoundInstance ?: return failPlayback()

        withMainContext {
            if (!capturedInstance.unpause()) soundManager.play(capturedInstance)
        }
        localClock.play()
        stateMachine.transition(PlayerState.PLAYING)
    }

    override suspend fun unstuckPlayback() {
        val capturedInstance = currentSoundInstance ?: return failPlayback()
        withMainContext {
            if (Minecraft.getInstance().level == null) return@withMainContext
            if (Minecraft.getInstance().isSingleplayer && Minecraft.getInstance().isPaused) return@withMainContext
            if (soundManager.isActive(capturedInstance)) return@withMainContext
            soundManager.play(capturedInstance)
        }
    }

    override suspend fun stopPlayback(isSeek: Boolean) {
        if (!isSeek && listening.compareAndSet(true, false)) notifier.listenerLeft()
        startPlaybackJob?.cancelAndJoin()
        startPlaybackJob = null

        val capturedStream = currentAudioEffectInputStream
        val capturedInstance = currentSoundInstance
        currentAudioEffectInputStream = null
        currentSoundInstance = null

        soundEventComposition.stopComposition()
        localClock.stop()
        isSeekingDisabled.set(false)

        withContext(NonCancellable) {
            withMainContext { capturedInstance?.let { soundManager.stop(it) } }
            capturedStream?.close()
            effectChain.reset()
            stateMachine.forceState(PlayerState.STOPPED)
        }
    }

    override suspend fun failPlayback(
        shouldDisableSeek: Boolean,
        shouldRetry: Boolean,
    ) {
        stopPlayback()
        isSeekingDisabled.set(shouldDisableSeek)
        if (shouldRetry) retryScheduler.scheduleRetry() else retryScheduler.reset()
    }

    override suspend fun applyStreamReady(intent: PlayerIntent.StreamReady) {
        consecutiveFailures.set(0)
        currentAudioEffectInputStream = intent.stream
        currentSoundInstance = intent.soundInstance

        if (intent.audioInfo.isLive) isSeekingDisabled.set(true)

        localClock.play(if (intent.audioInfo.isLive) 0.0 else intent.atPos)

        withMainContext { soundManager.play(intent.soundInstance) }
        soundEventComposition.makeComposition(intent.soundInstance)
        notifier.audioTitleChanged(intent.audioInfo.displayName)
        if (listening.compareAndSet(false, true)) notifier.listenerStarted()
        retryScheduler.reset()
    }

    override suspend fun notifyAudioFinished() = notifier.streamEnded()
}