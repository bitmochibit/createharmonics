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
import me.mochibit.createharmonics.audio.info.AudioInfo
import me.mochibit.createharmonics.foundation.err

/**
 * Runs ffprobe (bundled alongside ffmpeg) to extract container metadata from a URL
 * without downloading any audio data.
 *
 * Returns duration and title in a single fast probe call.
 */
class FFprobeExecutor {
    /**
     * Probes [url] and returns its duration (seconds) and title tag.
     * Returns null if ffprobe is unavailable or the probe fails.
     */
    suspend fun probe(url: String): AudioInfo =
        withContext(Dispatchers.IO) {
            try {
                val probePath =
                    FFMPEGProvider.ffprobePath
                        ?: throw IllegalStateException("FFprobeExecutor: ffprobe binary not found")

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
                    val (output, _) =
                        coroutineScope {
                            val stdout = async { process.inputStream.bufferedReader().use { it.readText() } }
                            val stderr = async { process.errorStream.bufferedReader().use { it.readText() } }
                            stdout.await() to stderr.await()
                        }

                    val exitCode = process.waitFor()

                    if (exitCode != 0 || output.isBlank()) throw IllegalStateException("FFprobeExecutor: Failed to probe")

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

                    AudioInfo(
                        audioUrl = url,
                        durationSeconds = duration,
                        title =
                            title ?: try {
                                val path = url.substringBefore('?').substringBefore('#')
                                val filename = path.substringAfterLast('/')
                                filename.ifBlank { "Unknown" }
                            } catch (_: Exception) {
                                "Unknown"
                            },
                        sampleRate = sampleRate,
                        false,
                    )
                } finally {
                    ProcessLifecycleManager.destroyProcess(processId)
                }
            } catch (e: Exception) {
                throw IllegalStateException("FFprobeExecutor: probe failed for $url: ${e.message}")
            }
        }
}
