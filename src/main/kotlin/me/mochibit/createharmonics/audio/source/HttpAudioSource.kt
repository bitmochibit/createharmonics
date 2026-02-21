package me.mochibit.createharmonics.audio.source

import me.mochibit.createharmonics.audio.process.FFprobeExecutor

/**
 * Audio source implementation for HTTP/HTTPS direct audio file URLs.
 *
 * Duration and title are resolved lazily via a single ffprobe call that reads
 * the container metadata without downloading any audio data.
 */
class HttpAudioSource(
    private val url: String,
) : AudioSource {
    /** Cached result of the ffprobe call; null means not yet probed. */
    private var probeInfo: FFprobeExecutor.ProbeInfo? = null
    private var probed = false

    private val ffprobe = FFprobeExecutor()

    private suspend fun ensureProbed() {
        if (!probed) {
            probeInfo = ffprobe.probe(url)
            probed = true
        }
    }

    override fun getIdentifier(): String = url

    override suspend fun resolveAudioUrl(): String = url

    override suspend fun getDurationSeconds(): Int {
        ensureProbed()
        return probeInfo?.durationSeconds ?: 0
    }

    override suspend fun getAudioName(): String {
        ensureProbed()
        val probedTitle = probeInfo?.title?.takeIf { it.isNotBlank() }
        if (probedTitle != null) return probedTitle

        // Fallback: derive a name from the URL path
        return try {
            val path = url.substringBefore('?').substringBefore('#')
            val filename = path.substringAfterLast('/')
            filename.ifBlank { "Unknown" }
        } catch (_: Exception) {
            "Unknown"
        }
    }

    override fun getMetadata(): Map<String, Any> =
        mapOf(
            "source" to "http",
            "url" to url,
            "duration" to (probeInfo?.durationSeconds ?: 0),
        )
}
