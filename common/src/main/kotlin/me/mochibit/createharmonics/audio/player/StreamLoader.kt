package me.mochibit.createharmonics.audio.player

import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.info.AudioInfo
import me.mochibit.createharmonics.audio.instance.SampleRatedInstance
import me.mochibit.createharmonics.audio.stream.AudioEffectInputStream
import me.mochibit.createharmonics.audio.upload.client.AudioNotFoundException
import me.mochibit.createharmonics.config.ClientConfig
import me.mochibit.createharmonics.foundation.debug
import net.minecraft.client.resources.sounds.SoundInstance
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * Stream load result
 * Sealed hierarchy — all subtypes are known at compile time, ideal for exhaustive `when`.
 *
 */
sealed interface StreamLoadResult {
    /**
     * Ready
     * Data class — a value holder; `equals`, `hashCode`, `toString` and `copy` are generated.
     *
     * @constructor Creates a new Ready
     * @property stream
     * @property soundInstance the sound instance
     * @property audioInfo the audio info
     * @property startedAt the started at
     */
    data class Ready(
        val stream: AudioEffectInputStream,
        val soundInstance: SoundInstance,
        val audioInfo: AudioInfo,
        val startedAt: Double,
    ) : StreamLoadResult

    /**
     * Finished
     * Singleton object — a single shared instance.
     *
     * @constructor Creates a new Finished
     */
    data object Finished : StreamLoadResult

    /**
     * Failed
     * Data class — a value holder; `equals`, `hashCode`, `toString` and `copy` are generated.
     *
     * @constructor Creates a new Failed
     * @property shouldDisableSeek the should disable seek
     * @property shouldRetry the should retry
     * @property permanent
     */
    data class Failed(
        val shouldDisableSeek: Boolean = false,
        val shouldRetry: Boolean = false,
        val permanent: Boolean = false,
    ) : StreamLoadResult
}

/**
 * Stream loader
 *
 * @constructor Creates a new StreamLoader
 * @property effectChain the effect chain
 * @property soundInstanceFactory the sound instance factory
 * @property player
 */
class StreamLoader(
    private val effectChain: EffectChain,
    private val soundInstanceFactory: SoundInstanceFactory,
    private val player: AudioPlayer,
) {
    /**
     * Returns
     * Suspending function — must be called from a coroutine or another suspending function.
     *
     * @param request
     * @param positionProvider the position provider
     * @receiver
     * @return the stream load result
     * @throws e
     */
    suspend fun load(request: AudioRequest, positionProvider: () -> Double): StreamLoadResult {
        val source = AudioSourceResolver.resolve(request)
        if (ClientConfig.debugAudioPlayer.get()) {
            "[STREAM LOADER] Source resolved :${source}".debug()
        }

        val resolved = try {
            SourceStreamResolver.resolveInputStream(source, positionProvider)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AudioNotFoundException) {
            logResolutionFailure(e)
            return StreamLoadResult.Failed(permanent = true)
        } catch (e: Exception) {
            logResolutionFailure(e)
            return StreamLoadResult.Failed()
        }

        return resolved.toLoadResult()
    }

    private fun SourceStreamResolver.Result.toLoadResult(): StreamLoadResult {
        val input = inputStream
        return when {
            status == SourceStreamResolver.Result.StreamStatus.FINISHED -> {
                input?.close()
                StreamLoadResult.Finished
            }
            input == null || status == SourceStreamResolver.Result.StreamStatus.FAILED -> {
                input?.close()
                StreamLoadResult.Failed(shouldDisableSeek = true, shouldRetry = true)
            }
            else -> buildReadyResult(input)
        }
    }

    private fun SourceStreamResolver.Result.buildReadyResult(input: InputStream): StreamLoadResult.Ready {
        val stream = AudioEffectInputStream(
            input,
            effectChain,
            audioInfo.sampleRate.toInt(),
            onStreamEnd = player::onStreamEnd,
            onStreamHang = player::onStreamHang,
        )
        val soundInstance = soundInstanceFactory(player, stream).also {
            if (it is SampleRatedInstance) it.sampleRate = audioInfo.sampleRate.toInt()
        }
        return StreamLoadResult.Ready(stream, soundInstance, audioInfo, startedAt)
    }

    private fun logResolutionFailure(e: Exception) {
        if (!ClientConfig.debugAudioPlayer.get()) return
        "[AUDIO PLAYER FAIL] cause: ${e.message ?: "no explicit cause"}".debug()
    }
}