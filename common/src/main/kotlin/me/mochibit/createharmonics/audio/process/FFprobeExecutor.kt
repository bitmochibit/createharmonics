package me.mochibit.createharmonics.audio.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.mochibit.createharmonics.Logger.err
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.audio.bin.BinProvider
import me.mochibit.createharmonics.audio.bin.FFMPEGProvider
import java.io.File

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
                        err("FFprobeExecutor: ffprobe binary not found")
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
                    val output = process.inputStream.bufferedReader().use { it.readText() }
                    val errorOutput = process.errorStream.bufferedReader().use { it.readText() }
                    val exitCode = process.waitFor()

                    if (exitCode != 0) {
                        err(
                            "FFprobeExecutor: ffprobe exited with code $exitCode for $url" +
                                if (errorOutput.isNotBlank()) ": ${errorOutput.take(200)}" else "",
                        )
                        return@withContext null
                    }

                    if (output.isBlank()) return@withContext null

                    val json = Json { ignoreUnknownKeys = true }
                    val root = json.parseToJsonElement(output).jsonObject

                    val format = root["format"]?.jsonObject

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

                    ProbeInfo(durationSeconds = duration, title = title ?: "")
                } finally {
                    ProcessLifecycleManager.destroyProcess(processId)
                }
            } catch (e: Exception) {
                err("FFprobeExecutor: probe failed for $url: ${e.message}")
                null
            }
        }
}
