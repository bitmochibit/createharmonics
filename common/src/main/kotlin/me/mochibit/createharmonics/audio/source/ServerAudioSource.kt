package me.mochibit.createharmonics.audio.source

import me.mochibit.createharmonics.audio.info.AudioInfo
import me.mochibit.createharmonics.audio.upload.ServerAudioReference
import me.mochibit.createharmonics.audio.upload.client.AudioNotFoundException
import me.mochibit.createharmonics.audio.upload.client.AudioServerClient
import me.mochibit.createharmonics.audio.upload.client.AudioServerEndpoint
import me.mochibit.createharmonics.audio.upload.client.ServerAudioCache

class ServerAudioSource(val ref: ServerAudioReference) : AudioSource {
    override suspend fun resolveAudioInfo(): AudioInfo {
        val audioMetadata = try {
            AudioServerClient.fetchInfo(ref)
        } catch (e: AudioNotFoundException) {
            ServerAudioCache.evict(ref)
            throw e
        }

        val local = ServerAudioCache.cachedFile(ref, audioMetadata.extension)
        val url = if (local != null) {
            local.toAbsolutePath().toString()
        } else {
            ServerAudioCache.prefetch(ref, audioMetadata.extension)
            AudioServerEndpoint.streamUrl(ref, audioMetadata.extension)
        }
        return AudioInfo(
            audioUrl = url,
            durationSeconds = audioMetadata.durationSeconds,
            displayName = audioMetadata.displayName,
            sampleRate = audioMetadata.sampleRate.toFloat(),
            isLive = false,
        )
    }
}