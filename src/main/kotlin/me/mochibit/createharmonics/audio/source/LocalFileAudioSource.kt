package me.mochibit.createharmonics.audio.source

import java.io.File

/**
 * Audio source implementation for local files.
 * Example of how easy it is to add new audio sources.
 */
class LocalFileAudioSource(
    private val filePath: String
) : AudioSource {

    override fun getIdentifier(): String = "file://$filePath"

    override suspend fun resolveAudioUrl(): String {
        val file = File(filePath)
        if (!file.exists()) {
            throw IllegalStateException("File not found: $filePath")
        }
        return file.absolutePath
    }

    override suspend fun getDurationSeconds(): Int {
        // For local files, we'll default to 0 (always stream)
        // Could use FFprobe here if needed
        return 0
    }

    override fun getMetadata(): Map<String, Any> {
        val file = File(filePath)
        return mapOf(
            "source" to "local_file",
            "path" to filePath,
            "size" to file.length(),
            "exists" to file.exists()
        )
    }
}