package me.mochibit.createharmonics.audio.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.mochibit.createharmonics.audio.bin.FFMPEGProvider
import me.mochibit.createharmonics.foundation.err

/**
 * Runs ffprobe (bundled alongside ffmpeg) to extract container metadata from a URL
 * without downloading any audio data.
 *
 * Returns duration and title in a single fast probe call.
 */
class FFprobeExecutor {
    data class ProbeInfo(
        val durationSeconds: Int,
        val title: String,
        val sampleRate: Float,
    )

    /**
     * Probes [url] and returns its duration (seconds) and title tag.
     * Returns null if ffprobe is unavailable or the probe fails.
     */
    suspend fun probe(url: String): ProbeInfo? =
        withContext(Dispatchers.IO) {
            try {
                val probePath =
                    FFMPEGProvider.ffprobePath ?: run {
                        "FFprobeExecutor: ffprobe binary not found".err()
                        return@withContext null
                    }

                val command =
                    listOf(
                        probePath,
                        "-v",
                        "quiet",
                        "-print_format",
                        "json",
                        "-show_entries",
                        "format=duration:format_tags=title",
                        "-user_agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                        url,
                    )

                val process =
                    ProcessBuilder(command)
                        .redirectErrorStream(false)
                        .start()

                val processId = ProcessLifecycleManager.registerProcess(process)

                try {
                    // Drain both pipes concurrently to prevent pipe-buffer deadlocks.
                    // Even with -v quiet, ffprobe may write to stderr; leaving it unread
                    // risks stalling the process if the OS buffer fills up.
                    val (output, _) =
                        coroutineScope {
                            val stdout = async { process.inputStream.bufferedReader().use { it.readText() } }
                            val stderr = async { process.errorStream.bufferedReader().use { it.readText() } }
                            stdout.await() to stderr.await()
                        }

                    val exitCode = process.waitFor()

                    if (exitCode != 0 || output.isBlank()) return@withContext null

                    val json = Json { ignoreUnknownKeys = true }
                    val root = json.parseToJsonElement(output).jsonObject

                    val format = root["format"]?.jsonObject
                    val streams = root["streams"]?.jsonArray

                    val duration =
                        format
                            ?.get("duration")
                            ?.jsonPrimitive
                            ?.content
                            ?.toDoubleOrNull()
                            ?.toInt() ?: 0

                    val title =
                        format
                            ?.get("tags")
                            ?.jsonObject
                            ?.get("title")
                            ?.jsonPrimitive
                            ?.content
                            ?.takeIf { it.isNotBlank() }

                    val sampleRate =
                        streams
                            ?.firstOrNull()
                            ?.jsonObject
                            ?.get("sample_rate")
                            ?.jsonPrimitive
                            ?.content
                            ?.toFloatOrNull() ?: 48_000f

                    ProbeInfo(durationSeconds = duration, title = title ?: "", sampleRate = sampleRate)
                } finally {
                    ProcessLifecycleManager.destroyProcess(processId)
                }
            } catch (e: Exception) {
                "FFprobeExecutor: probe failed for $url: ${e.message}".err()
                null
            }
        }
}
