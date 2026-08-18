package me.mochibit.createharmonics.audio.player

import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.info.AudioInfo
import me.mochibit.createharmonics.audio.instance.SampleRatedInstance
import me.mochibit.createharmonics.audio.stream.AudioEffectInputStream
import me.mochibit.createharmonics.config.ClientConfig
import me.mochibit.createharmonics.foundation.debug
import net.minecraft.client.resources.sounds.SoundInstance
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException

sealed interface StreamLoadResult {
    data class Ready(
        val stream: AudioEffectInputStream,
        val soundInstance: SoundInstance,
        val audioInfo: AudioInfo,
        val position: Double,
    ) : StreamLoadResult

    data object Finished : StreamLoadResult

    data class Failed(
        val shouldDisableSeek: Boolean = false,
        val shouldRetry: Boolean = false,
    ) : StreamLoadResult
}

class StreamLoader(
    private val effectChain: EffectChain,
    private val soundInstanceFactory: SoundInstanceFactory,
    private val player: AudioPlayer,
) {
    suspend fun load(request: AudioRequest, position: Double): StreamLoadResult {
        val source = AudioSourceResolver.resolve(request)

        val resolved = try {
            SourceStreamResolver.resolveInputStream(source, position)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logResolutionFailure(e)
            return StreamLoadResult.Failed()
        }

        return resolved.toLoadResult(position)
    }

    private fun SourceStreamResolver.Result.toLoadResult(position: Double): StreamLoadResult {
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
            else -> buildReadyResult(input, position)
        }
    }

    private fun SourceStreamResolver.Result.buildReadyResult(
        input: InputStream,
        position: Double,
    ): StreamLoadResult.Ready {
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
        return StreamLoadResult.Ready(stream, soundInstance, audioInfo, position)
    }

    private fun logResolutionFailure(e: Exception) {
        if (!ClientConfig.debugAudioPlayer.get()) return
        "[AUDIO PLAYER FAIL] cause: ${e.message ?: "no explicit cause"}".debug()
    }
}