package me.mochibit.createharmonics.audio.source

import me.mochibit.createharmonics.audio.info.AudioInfo

/**
 * Interface representing an audio source that can provide raw audio data.
 * Implementations can include YouTube, local files, HTTP streams, etc.
 */
sealed interface AudioSource {
    suspend fun resolveAudioInfo(): AudioInfo
}
