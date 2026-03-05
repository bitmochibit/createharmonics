package me.mochibit.createharmonics.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.mochibit.createharmonics.audio.comp.SoundEventComposition
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.process.FFmpegExecutor
import me.mochibit.createharmonics.audio.stream.AudioEffectInputStream
import me.mochibit.createharmonics.foundation.err
import net.minecraft.client.resources.sounds.SoundInstance

class PlaybackSession(
    val source: AudioPlaybackSource,
    val effectChain: EffectChain,
    val composition: SoundEventComposition,
    val offsetSeconds: Double,
) {
    var ffmpegExecutor: FFmpegExecutor? = null
    var audioStream: AudioEffectInputStream? = null
    var soundInstance: SoundInstance? = null
    var hasRetried = false

    @Volatile private var closed = false
    val isActive get() = !closed

    fun isUrlSessionAlive() = ffmpegExecutor?.isRunning() != false

    suspend fun close(tag: String = "") {
        if (closed) return
        closed = true

        val executor = ffmpegExecutor.also { ffmpegExecutor = null }
        val stream = audioStream.also { audioStream = null }
        soundInstance = null

        withContext(Dispatchers.IO) {
            runCatching { composition.stopComposition() }.onFailure { "$tag cleanup composition: ${it.message}".err() }
            runCatching { executor?.destroy() }.onFailure { "$tag cleanup ffmpeg: ${it.message}".err() }
            runCatching { stream?.close() }.onFailure { "$tag cleanup stream: ${it.message}".err() }
        }
    }
}
