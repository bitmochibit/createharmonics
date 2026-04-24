package me.mochibit.createharmonics.audio.player

import me.mochibit.createharmonics.audio.info.AudioInfo
import me.mochibit.createharmonics.audio.stream.AudioEffectInputStream
import net.minecraft.client.resources.sounds.SoundInstance

sealed interface PlayerIntent {
    data class Play(
        val initialPosition: Double,
    ) : PlayerIntent

    data object Pause : PlayerIntent

    data object Stop : PlayerIntent

    data object AudioHanged : PlayerIntent

    data object AudioFinished : PlayerIntent

    data object Shutdown : PlayerIntent

    data class Seek(
        val position: Double,
    ) : PlayerIntent

    data class NewRequest(
        val req: AudioRequest,
    ) : PlayerIntent

    object TailFinished : PlayerIntent

    data class StreamReady(
        val stream: AudioEffectInputStream,
        val soundInstance: SoundInstance,
        val audioInfo: AudioInfo,
        val atPos: Double,
    ) : PlayerIntent

    data class StreamFailed(
        val shouldDisableSeek: Boolean = false,
        val shouldRetry: Boolean = false,
    ) : PlayerIntent
}
