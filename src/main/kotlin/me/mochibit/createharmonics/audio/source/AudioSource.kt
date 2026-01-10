package me.mochibit.createharmonics.audio.source

/**
 * Interface representing an audio source that can provide raw audio data.
 * Implementations can include YouTube, local files, HTTP streams, etc.
 */
interface AudioSource {
    /**
     * Get a unique identifier for this audio source.
     */
    fun getIdentifier(): String

    /**
     * Extract or retrieve the actual audio URL/path that can be used for streaming.
     * This may involve API calls, URL extraction, or simple path resolution.
     */
    suspend fun resolveAudioUrl(): String

    /**
     * Get the duration of the audio in seconds.
     * Used to determine whether to stream or download the audio.
     */
    suspend fun getDurationSeconds(): Int

    /**
     * Optional: Get metadata about this audio source (title, duration, etc.)
     */
    fun getMetadata(): Map<String, Any> = emptyMap()

    /**
     * Get the name/title of the audio.
     * Returns "Unknown" by default if not available.
     */
    suspend fun getAudioName(): String = "Unknown"

    /**
     * Get HTTP headers required for streaming this audio source.
     * Returns empty map by default if no special headers are needed.
     */
    suspend fun getHttpHeaders(): Map<String, String> = emptyMap()
}
