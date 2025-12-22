package me.mochibit.createharmonics.audio.source

/**
 * Audio source implementation for HTTP/HTTPS direct audio streams.
 */
class HttpAudioSource(
    private val url: String,
) : AudioSource {
    override fun getIdentifier(): String = url

    override suspend fun resolveAudioUrl(): String = url

    override suspend fun getDurationSeconds(): Int = 0

    override fun getMetadata(): Map<String, Any> =
        mapOf(
            "source" to "http",
            "url" to url,
        )
}
