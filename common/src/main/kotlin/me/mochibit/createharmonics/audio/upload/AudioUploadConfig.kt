package me.mochibit.createharmonics.audio.upload

import java.nio.file.Path

/**
 * Config for audio upload server
 * todo: this should be interfaced (but in general all configs) so it becomes completely platform agnostic
 */
data class AudioUploadConfig(
    val bindHost: String = "0.0.0.0",
    val port: Int = 25566,
    val storageRoot: Path, // e.g. <server_root>/createharmonics/audio/uploaded
    val maxFilesPerPlayer: Int = 20,
    val maxFileSizeBytes: Long = 20L * 1024 * 1024,
    val allowedExtensions: Set<String> = setOf("mp3", "wav", "ogg", "flac", "m4a", "opus"),
) {
    init {
        require(port in 1..65535) { "Invalid port: $port" }
        require(maxFilesPerPlayer > 0) { "maxFilesPerPlayer must be > 0" }
        require(maxFileSizeBytes > 0) { "maxFileSizeBytes must be > 0" }
        require(allowedExtensions.isNotEmpty()) { "allowedExtensions must not be empty" }
        require(allowedExtensions.all { it.matches(Regex("^[a-z0-9]{1,8}$")) }) {
            "allowedExtensions must be lowercase alphanumeric"
        }
    }
}
