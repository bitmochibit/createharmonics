package me.mochibit.createharmonics.audio.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.mochibit.createharmonics.Logger.err
import me.mochibit.createharmonics.audio.binProvider.YTDLProvider

class YTdlpExecutor {
    data class YoutubeAudioInfo(
        val audioUrl: String,
        val durationSeconds: Int,
    )

    suspend fun extractAudioInfo(youtubeUrl: String): YoutubeAudioInfo? =
        withContext(Dispatchers.IO) {
            try {
                if (!YTDLProvider.isAvailable()) {
                    return@withContext null
                }

                val ytdlPath =
                    YTDLProvider.getExecutablePath()
                        ?: return@withContext null

                val command =
                    listOf(
                        ytdlPath,
                        // Format selection - use fastest format selection
                        // Prefer opus/m4a/webm audio, exclude HLS/DASH streaming (slower to parse)
                        "-f",
                        "bestaudio[ext=opus]/bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio/best",
                        // Output options - only what we need
                        "--get-url",
                        "--get-duration",
                        // Performance optimizations
                        "--no-playlist", // Don't process playlists
                        "--no-check-certificates", // Skip SSL cert validation for speed
                        "--prefer-free-formats", // Prefer formats that don't require extra processing
                        "--extractor-retries",
                        "3", // Limit retries to avoid hanging
                        "--socket-timeout",
                        "10", // 10 second socket timeout
                        // Reduce unnecessary processing
                        "--no-warnings", // Don't print warnings
                        "--quiet", // Minimal output for faster execution
                        "--no-call-home", // Don't check for updates
                        // Skip geo-bypass attempts (saves time)
                        "--no-geo-bypass",
                        // Skip checking if video needs login
                        "--no-check-formats",
                        youtubeUrl,
                    )

                val process =
                    ProcessBuilder(command)
                        .redirectErrorStream(false)
                        .start()

                val processId = ProcessLifecycleManager.registerProcess(process)

                try {
                    val output = process.inputStream.bufferedReader().use { it.readText() }
                    val errorOutput = process.errorStream.bufferedReader().use { it.readText() }

                    val exitCode = process.waitFor()

                    if (exitCode != 0) {
                        err("yt-dlp failed with exit code $exitCode")
                        // Only log errors if there was a failure
                        if (errorOutput.isNotBlank()) {
                            err("yt-dlp error: ${errorOutput.take(500)}") // Limit error message length
                        }
                        return@withContext null
                    }

                    val lines = output.trim().lines()
                    val audioUrl = lines.firstOrNull { it.startsWith("http") }
                    val durationStr = lines.lastOrNull { it.contains(":") } // Format: HH:MM:SS or MM:SS

                    if (audioUrl == null || durationStr == null) {
                        err("Failed to parse yt-dlp output. Lines: $lines")
                        return@withContext null
                    }

                    val durationSeconds = parseDuration(durationStr)
                    YoutubeAudioInfo(audioUrl, durationSeconds)
                } finally {
                    ProcessLifecycleManager.destroyProcess(processId)
                }
            } catch (e: Exception) {
                err("Error extracting audio info: ${e.message}")
                e.printStackTrace()
                null
            }
        }

    private fun parseDuration(duration: String): Int {
        val parts = duration.split(":").map { it.toIntOrNull() ?: 0 }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]

            // HH:MM:SS
            2 -> parts[0] * 60 + parts[1]

            // MM:SS
            1 -> parts[0]

            // SS
            else -> 0
        }
    }
}
