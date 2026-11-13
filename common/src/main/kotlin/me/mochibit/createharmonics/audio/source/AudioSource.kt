package me.mochibit.createharmonics.audio.source

import me.mochibit.createharmonics.audio.info.AudioInfo

/**
 * Interface representing an audio source that can provide raw audio data.
 * Implementations can include YouTube, local files, HTTP streams, etc.
 */
sealed interface AudioSource {
    /**
     * Resolve audio info
     * Suspending function — must be called from a coroutine or another suspending function.
     *
     * @return the audio info
     */
    suspend fun resolveAudioInfo(): AudioInfo
}
