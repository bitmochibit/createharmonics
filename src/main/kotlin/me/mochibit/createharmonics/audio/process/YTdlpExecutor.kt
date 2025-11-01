package me.mochibit.createharmonics.audio.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.mochibit.createharmonics.Logger.err
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.audio.binProvider.YTDLBin

/**
 * Wrapper for yt-dlp operations with managed process lifecycle.
 * Provides high-level operations for extracting YouTube metadata and URLs.
 */
class YTdlpExecutor {

    /**
     * Result of extracting YouTube audio information.
     */
    data class YoutubeAudioInfo(
        val audioUrl: String,
        val durationSeconds: Int
    )

    /**
     * Ensure yt-dlp is installed and available.
     */
    suspend fun ensureInstalled(): Boolean = withContext(Dispatchers.IO) {
        if (!YTDLBin.isAvailable()) {
            info("yt-dlp not found, installing...")
            if (!YTDLBin.install()) {
                err("Failed to install yt-dlp")
                return@withContext false
            }
            info("yt-dlp installed successfully")
        }
        true
    }

    /**
     * Extract audio URL and duration from YouTube video.
     */
    suspend fun extractAudioInfo(youtubeUrl: String): YoutubeAudioInfo? = withContext(Dispatchers.IO) {
        try {
            if (!ensureInstalled()) {
                return@withContext null
            }

            val ytdlPath = YTDLBin.getExecutablePath()
                ?: return@withContext null

            // Use format selector to prefer direct URLs over HLS/DASH manifests
            val command = listOf(
                ytdlPath,
                "-f", "ba[protocol!*=m3u8][protocol!*=dash]/ba/b",
                "--get-url",
                "--get-duration",
                "--no-playlist",
                "--verbose",
                youtubeUrl
            )

            val process = ProcessBuilder(command)
                .redirectErrorStream(false)
                .start()

            val processId = ProcessLifecycleManager.registerProcess(process)

            try {
                val output = process.inputStream.bufferedReader().use { it.readText() }
                val errorOutput = process.errorStream.bufferedReader().use { it.readText() }

                // Log yt-dlp output for debugging
                if (errorOutput.isNotBlank()) {
                    errorOutput.lines().forEach { line ->
                        if (line.isNotBlank()) {
                            info("yt-dlp: $line")
                        }
                    }
                }

                val exitCode = process.waitFor()

                if (exitCode != 0) {
                    err("yt-dlp failed with exit code $exitCode")
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
                ProcessLifecycleManager.unregisterProcess(processId)
                if (process.isAlive) {
                    process.destroy()
                }
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
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2] // HH:MM:SS
            2 -> parts[0] * 60 + parts[1] // MM:SS
            1 -> parts[0] // SS
            else -> 0
        }
    }
}

