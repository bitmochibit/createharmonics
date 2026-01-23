package me.mochibit.createharmonics.audio.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.mochibit.createharmonics.Logger.err
import me.mochibit.createharmonics.audio.bin.YTDLProvider

class YTdlpExecutor {
    data class AudioUrlInfo(
        val audioUrl: String,
        val durationSeconds: Int,
        val title: String,
        val httpHeaders: Map<String, String> = emptyMap(),
    )

    suspend fun extractAudioInfo(youtubeUrl: String): AudioUrlInfo? =
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
                        // Format selection - explicitly avoid HLS/DASH/m3u8 formats
                        // Prefer direct HTTP progressive downloads
                        "-f",
                        "bestaudio[protocol^=http][protocol!*=m3u8]/bestaudio[protocol=https]/bestaudio[ext!=m3u8]/bestaudio/best",
                        // Output in JSON format to get all metadata including HTTP headers
                        "-j",
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

                    // Parse JSON output
                    val json = Json { ignoreUnknownKeys = true }
                    val jsonElement = json.parseToJsonElement(output)
                    val jsonObject = jsonElement.jsonObject

                    val title = jsonObject["title"]?.jsonPrimitive?.content ?: "Unknown"
                    val audioUrl =
                        jsonObject["url"]?.jsonPrimitive?.content
                            ?: throw IllegalStateException("No URL found in yt-dlp output")

                    // Duration can be either int or double, convert to int seconds
                    val duration =
                        try {
                            jsonObject["duration"]?.jsonPrimitive?.int ?: 0
                        } catch (e: Exception) {
                            // Try parsing as double and convert to int
                            jsonObject["duration"]
                                ?.jsonPrimitive
                                ?.content
                                ?.toDoubleOrNull()
                                ?.toInt() ?: 0
                        }

                    // Extract HTTP headers if available
                    val httpHeaders = mutableMapOf<String, String>()
                    jsonObject["http_headers"]?.jsonObject?.let { headers ->
                        headers.forEach { (key, value) ->
                            httpHeaders[key] = value.jsonPrimitive.content
                        }
                    }

                    AudioUrlInfo(audioUrl, duration, title, httpHeaders)
                } finally {
                    ProcessLifecycleManager.destroyProcess(processId)
                }
            } catch (e: Exception) {
                err("Error extracting audio info: ${e.message}")
                e.printStackTrace()
                null
            }
        }
}
