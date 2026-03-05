package me.mochibit.createharmonics.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.mochibit.createharmonics.audio.cache.AudioInfoCache
import me.mochibit.createharmonics.audio.comp.SoundEventComposition
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.instance.SampleRatedInstance
import me.mochibit.createharmonics.audio.process.FFmpegExecutor
import me.mochibit.createharmonics.audio.stream.AudioEffectInputStream
import me.mochibit.createharmonics.foundation.async.modLaunch
import me.mochibit.createharmonics.foundation.async.withMainContext
import me.mochibit.createharmonics.foundation.debug
import me.mochibit.createharmonics.foundation.err
import me.mochibit.createharmonics.foundation.extension.ticks
import me.mochibit.createharmonics.foundation.network.packet.AudioPlayerStreamEndPacket
import me.mochibit.createharmonics.foundation.network.packet.UpdateAudioNamePacket
import me.mochibit.createharmonics.foundation.registry.ModPackets
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import java.io.InputStream

typealias StreamId = String
typealias StreamingSoundInstanceProvider = (streamId: StreamId, stream: InputStream) -> SoundInstance

class AudioPlayer internal constructor(
    override val playerId: String,
    private val soundInstanceProvider: StreamingSoundInstanceProvider,
    val sampleRate: Int = 48_000,
    private val callbacks: AudioPlayerCallbacks = AudioPlayerCallbacks(),
) : IAudioPlayer {
    private val _state = MutableStateFlow<AudioPlayerState>(AudioPlayerState.Stopped)
    override val stateFlow = _state.asStateFlow()
    override val state get() = _state.value

    private val mutex = Mutex()
    private var session: PlaybackSession? = null

    private val soundManager get() = Minecraft.getInstance().soundManager

    override val currentEffectChain: EffectChain?
        get() = session?.effectChain

    override fun play(
        url: String,
        effectChain: EffectChain,
        composition: SoundEventComposition,
        offsetSeconds: Double,
    ) = modLaunch(Dispatchers.IO) {
        startSession(AudioPlaybackSource.FromUrl(url), effectChain, composition, offsetSeconds)
    }

    override fun playFromStream(
        inputStream: InputStream,
        label: String,
        effectChain: EffectChain,
        composition: SoundEventComposition,
        offsetSeconds: Double,
        sampleRateOverride: Int?,
    ) = modLaunch(Dispatchers.IO) {
        startSession(
            AudioPlaybackSource.FromStream(inputStream, label, sampleRateOverride),
            effectChain,
            composition,
            offsetSeconds,
        )
    }

    override fun stop() =
        modLaunch(Dispatchers.IO) {
            val instance =
                mutex.withLock {
                    if (!state.canStop()) return@modLaunch
                    session?.soundInstance.also { closeSessionInternal() }
                }
            instance?.stopOnMainThread()
        }

    override fun pause() =
        modLaunch {
            val (instance, comp) =
                mutex.withLock {
                    if (!state.canPause()) return@modLaunch
                    val s = session ?: return@modLaunch
                    transitionTo(AudioPlayerState.Paused)
                    s.soundInstance to s.composition
                }
            runCatching {
                withMainContext {
                    instance?.let { soundManager.stop(it) }
                    comp.stopComposition()
                }
            }.onFailure { "$playerId pause: ${it.message}".err() }
        }

    override fun resume() =
        modLaunch {
            val (instance, comp) =
                mutex.withLock {
                    if (!state.canResume()) return@modLaunch
                    val s =
                        session ?: run {
                            transitionTo(AudioPlayerState.Stopped)
                            return@modLaunch
                        }

                    if (s.soundInstance == null || s.audioStream == null || !s.isUrlSessionAlive()) {
                        closeSessionInternal()
                        notifyEnd(failed = true)
                        return@modLaunch
                    }

                    transitionTo(AudioPlayerState.Playing)
                    s.soundInstance!! to s.composition
                }
            runCatching {
                withMainContext {
                    soundManager.play(instance)
                    comp.makeComposition(instance)
                }
            }.onFailure {
                mutex.withLock { closeSessionInternal() }
                notifyEnd(failed = true)
            }
        }

    override fun dispose() =
        modLaunch(Dispatchers.IO) {
            val instance =
                mutex.withLock {
                    session?.soundInstance.also { closeSessionInternal() }
                }
            instance?.stopOnMainThread()
        }

    private suspend fun startSession(
        source: AudioPlaybackSource,
        effectChain: EffectChain,
        composition: SoundEventComposition,
        offsetSeconds: Double,
    ) {
        val newSession =
            mutex.withLock {
                if (isAlreadyPlaying(source, effectChain)) return
                if (state.canStop()) closeSessionInternal()

                transitionTo(AudioPlayerState.Loading)
                PlaybackSession(source, effectChain, composition, offsetSeconds).also {
                    callbacks.onEffectChainReady?.invoke(effectChain)
                    session = it
                }
            }

        val result =
            runCatching {
                when (source) {
                    is AudioPlaybackSource.FromUrl -> initUrlSession(source.url, newSession)
                    is AudioPlaybackSource.FromStream -> initStreamSession(source, newSession)
                }
            }

        mutex.withLock {
            if (session !== newSession) {
                "$playerId: stale session, discarding".debug()
                newSession.close(tag = playerId)
                return
            }
            result
                .onSuccess { transitionTo(AudioPlayerState.Playing) }
                .onFailure { e ->
                    "$playerId init error: ${e.message}".err()
                    val canRetry =
                        source is AudioPlaybackSource.FromUrl &&
                            !AudioSourceResolver.isDirect(source.url) &&
                            !newSession.hasRetried
                    if (canRetry) {
                        retryAfterCacheInvalidation(source.url, newSession)
                    } else {
                        closeSessionInternal()
                        notifyEnd(failed = true)
                    }
                }
        }
    }

    private suspend fun initUrlSession(
        url: String,
        session: PlaybackSession,
    ) {
        val audioSource = AudioSourceResolver.resolve(url)
        audioSource.getAudioName().takeIf { it != "Unknown" }?.let {
            ModPackets.sendToServer(UpdateAudioNamePacket(playerId, it))
        }

        val duration = audioSource.getDurationSeconds()
        val offset =
            if (duration > 0 && session.offsetSeconds >= duration) {
                session.offsetSeconds % duration
            } else {
                session.offsetSeconds
            }

        val ffmpeg = FFmpegExecutor().also { session.ffmpegExecutor = it }
        if (!ffmpeg.createStream(url, sampleRate, offset, audioSource.getHttpHeaders())) {
            throw IllegalStateException("FFmpeg stream init failed")
        }

        val rawStream = ffmpeg.inputStream ?: throw IllegalStateException("FFmpeg inputStream is null")
        session.audioStream = buildEffectStream(rawStream, session)
        startPlayback(session)
    }

    private suspend fun initStreamSession(
        source: AudioPlaybackSource.FromStream,
        session: PlaybackSession,
    ) {
        source.label.takeIf { it.isNotBlank() && it != "Unknown" }?.let {
            ModPackets.sendToServer(UpdateAudioNamePacket(playerId, it))
        }

        val effectiveSampleRate = source.sampleRateOverride ?: sampleRate
        if (session.offsetSeconds > 0.0) {
            skipStreamOffset(source.stream, session.offsetSeconds, effectiveSampleRate)
        }

        session.audioStream = buildEffectStream(source.stream, session, source.sampleRateOverride)
        startPlayback(session, source.sampleRateOverride)
    }

    private suspend fun skipStreamOffset(
        stream: InputStream,
        offsetSeconds: Double,
        sr: Int,
    ) = withContext(Dispatchers.IO) {
        var remaining = (offsetSeconds * sr * 2L).let { if ((it % 2).toLong() != 0L) it + 1 else it }
        val buf = ByteArray(8_192)
        while (remaining > 0) {
            val read = stream.read(buf, 0, minOf(remaining, buf.size.toDouble()).toInt())
            if (read <= 0) break
            remaining -= read
        }
    }

    private suspend fun startPlayback(
        session: PlaybackSession,
        sampleRateOverride: Int? = null,
    ) {
        val stream = session.audioStream ?: throw IllegalStateException("No audio stream")
        val instance =
            soundInstanceProvider(playerId, stream).apply {
                if (this is SampleRatedInstance) sampleRate = sampleRateOverride ?: this@AudioPlayer.sampleRate
            }
        session.soundInstance = instance
        withMainContext {
            session.composition.makeComposition(instance)
            soundManager.play(instance)
        }
    }

    private fun buildEffectStream(
        raw: InputStream,
        session: PlaybackSession,
        srOverride: Int? = null,
    ) = AudioEffectInputStream(
        raw,
        session.effectChain,
        srOverride ?: sampleRate,
        onStreamEnd = { handleStreamEnd() },
        onStreamHang = { handleStreamHang() },
    )

    private fun handleStreamEnd() =
        modLaunch(Dispatchers.IO) {
            mutex.withLock {
                when (state) {
                    is AudioPlayerState.Playing -> {
                        closeSessionInternal()
                        notifyEnd(failed = false)
                    }

                    is AudioPlayerState.Paused -> {
                        closeSessionInternal()
                        notifyEnd(failed = true)
                    }

                    else -> {
                    }
                }
            }
        }

    private fun handleStreamHang() {
        if (state !is AudioPlayerState.Playing) return
        modLaunch(Dispatchers.IO) {
            delay(1.ticks())
            val instance = mutex.withLock { session?.soundInstance } ?: return@modLaunch
            runCatching { withMainContext { soundManager.play(instance) } }
                .onFailure { "$playerId hang restart: ${it.message}".err() }
        }
    }

    private suspend fun retryAfterCacheInvalidation(
        url: String,
        currentSession: PlaybackSession,
    ) {
        currentSession.hasRetried = true
        AudioInfoCache
            .invalidate(url)
        delay(500)
        runCatching { initUrlSession(url, currentSession) }
            .onSuccess { transitionTo(AudioPlayerState.Playing) }
            .onFailure {
                "$playerId retry failed: ${it.message}".err()
                closeSessionInternal()
                notifyEnd(failed = true)
            }
    }

    private fun isAlreadyPlaying(
        source: AudioPlaybackSource,
        effectChain: EffectChain,
    ): Boolean {
        if (state !is AudioPlayerState.Playing) return false
        return session?.let { it.source == source && it.effectChain == effectChain } ?: false
    }

    private fun closeSessionInternal() {
        session?.let { s ->
            session = null
            modLaunch(Dispatchers.IO) { s.close(tag = playerId) }
        }
        transitionTo(AudioPlayerState.Stopped)
    }

    private fun transitionTo(next: AudioPlayerState) {
        val prev = _state.value
        if (prev == next) return
        _state.value = next
        callbacks.onStateChange?.invoke(playerId, prev, next)
    }

    private fun notifyEnd(failed: Boolean) {
        callbacks.onStreamEnd?.invoke(playerId, failed)
        ModPackets.sendToServer(AudioPlayerStreamEndPacket(playerId, failed))
    }

    private suspend fun SoundInstance.stopOnMainThread() =
        runCatching { withMainContext { soundManager.stop(this@stopOnMainThread) } }
            .onFailure { "$playerId stop error: ${it.message}".err() }
}
