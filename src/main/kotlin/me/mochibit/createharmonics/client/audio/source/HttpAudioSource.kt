package me.mochibit.createharmonics.client.audio.source

/**
 * Audio source implementation for HTTP/HTTPS direct audio streams.
 * Example of how easy it is to add new audio sources.
 */
class HttpAudioSource(
    private val url: String
) : AudioSource {

    override fun getIdentifier(): String = url

    override suspend fun resolveAudioUrl(): String {
        // For HTTP sources, the URL is already the audio URL
        // You could add validation here if needed
        return url
    }

    override suspend fun getDurationSeconds(): Int {
        // For HTTP streams, we'll default to 0 (always stream)
        // Could make a HEAD request to check content-length if needed
        return 0
    }

    override fun getMetadata(): Map<String, Any> {
        return mapOf(
            "source" to "http",
            "url" to url
        )
    }
}