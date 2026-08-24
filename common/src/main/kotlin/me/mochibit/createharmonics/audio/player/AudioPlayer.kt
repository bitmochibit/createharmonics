package me.mochibit.createharmonics.audio.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.mochibit.createharmonics.audio.comp.SoundEventComposition
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.instance.AudioPlayerSoundInstance
import me.mochibit.createharmonics.audio.stream.AudioEffectInputStream
import me.mochibit.createharmonics.audio.utils.pause
import me.mochibit.createharmonics.audio.utils.unpause
import me.mochibit.createharmonics.foundation.async.ClientCoroutineScope
import me.mochibit.createharmonics.foundation.async.withMainContext
import me.mochibit.createharmonics.foundation.info
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

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
    private val streamResolutionStartMillis = AtomicLong(0)

    @Volatile
    private var lastResyncAt: Long = -1L
    private val resyncCooldown = 10.seconds

    val playerTerminated = AtomicBoolean(false)
    val isSeekingDisabled = AtomicBoolean(false)

    @Volatile
    var spatialContext: AudioSpatialContext? = null

    @Volatile
    var spatialContextKey: Any? = null

    val effectChain = EffectChain()
    val soundEventComposition = SoundEventComposition(soundEffectChain = effectChain)
    val clock = PlaytimeClock()

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

    fun syncWith(other: PlaytimeClock) {
        if (!clock.isPlaying || !other.isPlaying) return
        val now = System.currentTimeMillis()
        if (lastResyncAt != -1L && now - lastResyncAt < resyncCooldown.inWholeMilliseconds) return
        val drift = other.currentPlaytime - clock.currentPlaytime
        if (abs(drift) > 2.0) {
            lastResyncAt = now
            seek(other.currentPlaytime)
        }
    }

    fun tick() {
        clock.tick()
        spatial.tick(this)
    }

    fun close() {
        playerTerminated.set(true)
        retryScheduler.cancelPending()
        watchdog.stop()
        stateMachine.shutdown()
    }

    internal fun onStreamEnd() = stateMachine.send(PlayerIntent.AudioFinished)

    internal fun onStreamHang() = stateMachine.send(PlayerIntent.AudioHanged)

    override fun hasActiveSoundInstance(): Boolean = currentSoundInstance != null

    override fun isSeekingDisabled(): Boolean = isSeekingDisabled.get()

    override fun currentPlaytime(): Double = clock.currentPlaytime

    override fun isSameSourceAsCurrentRequest(request: AudioRequest): Boolean =
        currentAudioRequest?.isSameSource(request) == true

    override fun isValidGeneration(streamGeneration: Int): Boolean = streamGeneration == loadingGeneration.get()

    override fun setCurrentRequest(request: AudioRequest) {
        currentAudioRequest = request
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
        streamResolutionStartMillis.set(System.currentTimeMillis())
        stateMachine.transition(PlayerState.LOADING)

        startPlaybackJob =
            playerScope.launch(Dispatchers.IO) {
                when (val result = streamLoader.load(request, position)) {
                    is StreamLoadResult.Ready -> {
                        if (!isActive) {
                            result.stream.close()
                            return@launch
                        }
                        stateMachine.send(
                            PlayerIntent.StreamReady(result.stream, result.soundInstance, result.audioInfo, position, generation),
                        )
                    }

                    StreamLoadResult.Finished -> stateMachine.send(PlayerIntent.AudioFinished)

                    is StreamLoadResult.Failed -> {
                        if (position > 0) {
                            "Restarted source playback since it hanged, probably for this url seek is not supported".info()
                        }
                        if (isActive) {
                            stateMachine.send(PlayerIntent.StreamFailed(result.shouldDisableSeek, result.shouldRetry))
                        }
                    }
                }
            }
    }

    override suspend fun pausePlayback() {
        val capturedInstance = currentSoundInstance ?: return failPlayback()

        clock.pause()
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
        clock.play()
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
        startPlaybackJob?.cancelAndJoin()
        startPlaybackJob = null

        val capturedStream = currentAudioEffectInputStream
        val capturedInstance = currentSoundInstance
        currentAudioEffectInputStream = null
        currentSoundInstance = null

        soundEventComposition.stopComposition()
        clock.stop()
        isSeekingDisabled.set(false)
        if (!isSeek) lastResyncAt = -1L

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
        currentAudioEffectInputStream = intent.stream
        currentSoundInstance = intent.soundInstance

        if (intent.audioInfo.isLive) isSeekingDisabled.set(true)

        val resolutionElapsed = (System.currentTimeMillis() - streamResolutionStartMillis.get()) / 1000.0
        val adjustedPos = if (intent.audioInfo.isLive) 0.0 else intent.atPos + resolutionElapsed
        clock.play(adjustedPos)
        lastResyncAt = System.currentTimeMillis()

        withMainContext { soundManager.play(intent.soundInstance) }
        soundEventComposition.makeComposition(intent.soundInstance)
        notifier.audioTitleChanged(intent.audioInfo.title)
        retryScheduler.reset()
    }

    override suspend fun notifyAudioFinished() = notifier.streamEnded()
}