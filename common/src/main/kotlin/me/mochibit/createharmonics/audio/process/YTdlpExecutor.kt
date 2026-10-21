package me.mochibit.createharmonics.audio.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.mochibit.createharmonics.audio.bin.FFMPEGProvider
import me.mochibit.createharmonics.audio.bin.YTDLProvider
import me.mochibit.createharmonics.audio.info.AudioInfo
import me.mochibit.createharmonics.config.ModConfigs
import me.mochibit.createharmonics.foundation.err
import java.nio.file.Files
import java.nio.file.Path

data class DownloadedAudio(val file: Path, val title: String?, val artist: String?)

object YTdlpExecutor {
    private const val SEGMENT_DURATION_CEILING_S = 300


    suspend fun extractAudioInfo(youtubeUrl: String): AudioInfo =
        withContext(Dispatchers.IO) {
            try {
                if (!YTDLProvider.isAvailable()) throw IllegalStateException("YTDLProvider not available")

                val ytdlPath = YTDLProvider.getExecutablePath() ?: throw IllegalStateException("YTDLP not found")
                val configOverrides = ModConfigs.client.ytdlpOverrideArgs.get()

                val command =
                    if (configOverrides.isNotBlank()) {
                        buildList {
                            add(ytdlPath)
                            addAll(configOverrides.split(" "))
                            add("-j")
                            add(youtubeUrl)
                        }
                    } else {
                        listOf(
                            ytdlPath,
                            "-f",
                            "bestaudio[ext!=flv][ext=fmp4][protocol!=m3u8_native][protocol!=m3u8]/bestaudio/best",
                            "--no-check-formats",
                            "-j",
                            "--quiet",
                            "--no-playlist",
                            "--skip-download",
                            youtubeUrl,
                        )
                    }

                val process =
                    ProcessBuilder(command)
                        .redirectErrorStream(false)
                        .start()

                val processId = ProcessLifecycleManager.registerProcess(process)

                try {
                    // Read stdout and stderr concurrently to prevent pipe-buffer deadlocks.
                    // Sequential reads can deadlock when the process fills one pipe's OS buffer
                    // while we're blocked draining the other.
                    val (output, errorOutput) =
                        coroutineScope {
                            val stdout = async { process.inputStream.bufferedReader().use { it.readText() } }
                            val stderr = async { process.errorStream.bufferedReader().use { it.readText() } }
                            stdout.await() to stderr.await()
                        }

                    val exitCode = process.waitFor()

                    if (exitCode != 0) {
                        "yt-dlp failed with exit code $exitCode".err()
                        if (errorOutput.isNotBlank()) {
                            "yt-dlp error: ${errorOutput.take(500)}".err()
                        }
                        throw IllegalStateException("Ytdlp failed with exit code $exitCode")
                    }

                    val firstJsonLine =
                        output.lineSequence().firstOrNull { it.trimStart().startsWith("{") }
                            ?: run {
                                "yt-dlp output contained no JSON object".err()
                                throw IllegalStateException("Ytdlp produced no json object")
                            }

                    val json = Json { ignoreUnknownKeys = true }
                    val jsonObject = json.parseToJsonElement(firstJsonLine).jsonObject

                    val title = jsonObject["title"]?.jsonPrimitive?.content ?: "Unknown"
                    val artist = jsonObject["artist"]?.jsonPrimitive?.content ?: "Unknown artist"
                    val audioUrl =
                        when (jsonObject["protocol"]?.jsonPrimitive?.content) {
                            "m3u8_native", "m3u8" -> {
                                jsonObject["manifest_url"]?.jsonPrimitive?.content
                                    ?: jsonObject["url"]?.jsonPrimitive?.content
                                    ?: throw IllegalStateException("No URL found in yt-dlp output")
                            }

                            else -> {
                                jsonObject["url"]?.jsonPrimitive?.content
                                    ?: throw IllegalStateException("No URL found in yt-dlp output")
                            }
                        }
                    val sampleRate = jsonObject["asr"]?.jsonPrimitive?.floatOrNull ?: 48_000f
                    val duration = extractFullDuration(jsonObject)

                    val isLive = jsonObject["is_live"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

                    val httpHeaders =
                        buildMap {
                            jsonObject["http_headers"]?.jsonObject?.forEach { (key, value) ->
                                put(key, value.jsonPrimitive.content)
                            }
                        }

                    AudioInfo(
                        audioUrl,
                        durationSeconds = duration,
                        title = title,
                        artist = artist,
                        sampleRate = sampleRate,
                        isLive = isLive,
                        httpHeaders = httpHeaders,
                    )
                } finally {
                    ProcessLifecycleManager.destroyProcess(processId)
                }
            } catch (e: Exception) {
                throw IllegalStateException("Error extracting audio info: ${e.message}")
            }
        }

    suspend fun download(url: String, targetDir: Path): DownloadedAudio {
        if (!YTDLProvider.isAvailable()) error("YTDLProvider not available")
        val ytdlPath = YTDLProvider.getExecutablePath() ?: error("YTDLP not found")

        val command = buildList {
            add(ytdlPath)
            addAll(listOf("--encoding", "utf-8"))
            addAll(listOf("-f", "bestaudio/best"))
            addAll(listOf("-x", "--audio-format", "mp3"))
            add("--no-playlist")
            addAll(listOf("--match-filter", "!is_live"))
            FFMPEGProvider.ffprobePath?.let { Path.of(it).parent }?.let {
                addAll(listOf("--ffmpeg-location", it.toString()))
            }
            addAll(listOf("-P", targetDir.toAbsolutePath().toString()))
            addAll(listOf("-o", "%(id)s.%(ext)s", "--restrict-filenames"))
            addAll(listOf("--no-simulate", "--print", "after_move:%()j"))
            add("--")
            add(url)
        }

        val result = runProcess(command)
        if (result.exitCode != 0) {
            "yt-dlp error: ${result.stderr.take(500)}".err()
            throw IllegalStateException("yt-dlp failed with exit code ${result.exitCode}")
        }

        val line = result.stdout.lineSequence().lastOrNull { it.trimStart().startsWith("{") }
            ?: throw IllegalStateException("yt-dlp produced no output (live stream or filtered video?)")
        val info = Json.parseToJsonElement(line).jsonObject

        val file = info["filepath"]?.jsonPrimitive?.contentOrNull?.let(Path::of)
            ?.takeIf { Files.isRegularFile(it) }
            ?: throw IllegalStateException("yt-dlp did not report the downloaded file")

        fun str(key: String) = info[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        return DownloadedAudio(
            file = file,
            title = str("title"),
            artist = str("artist") ?: str("creator") ?: str("uploader"),
        )
    }

    /**
     * Extracts the *full* video/audio duration (in whole seconds) from a yt-dlp info-dict
     * JSON object.
     *
     * yt-dlp's `-j` output merges the selected format dict into the root info dict, which
     * means the root-level `"duration"` field can be overwritten by the format's own
     * `"duration"` value. For segmented DASH/HLS formats that value is the per-fragment
     * length (commonly ~85 s for YouTube), not the full video duration.
     *
     * We therefore try multiple fields in descending order of reliability:
     *  1. `"duration_string"` – a human-readable `"H:MM:SS"` field that is set from the
     *     info dict and is **not** overwritten by format merging.
     *  2. `"duration"` as a numeric value, accepted only when it is large enough to be
     *     the real duration (> the fragment threshold).
     *  3. The maximum `"duration"` found across all entries in the `"formats"` array.
     *  4. `0` (unknown) as a last resort.
     */
    private fun extractFullDuration(jsonObject: JsonObject): Int {
        // 1. duration_string is "H:MM:SS" or "M:SS" – always refers to the full video
        val durationString = jsonObject["duration_string"]?.jsonPrimitive?.content
        if (!durationString.isNullOrBlank()) {
            val parsed = parseDurationString(durationString)
            if (parsed > 0) return parsed
        }

        // 2. Numeric "duration" – only trust it when it looks like a full-video length.
        //    A value ≤ SEGMENT_DURATION_CEILING_S is suspiciously small and likely a
        //    per-segment length injected by the format merge.
        val numericDuration =
            runCatching {
                jsonObject["duration"]
                    ?.jsonPrimitive
                    ?.content
                    ?.toDoubleOrNull()
                    ?.toInt() ?: 0
            }.getOrDefault(0)

        if (numericDuration > SEGMENT_DURATION_CEILING_S) return numericDuration

        // 3. Scan the "formats" array and take the maximum reported duration
        val maxFormatDuration =
            (jsonObject["formats"] as? JsonArray)
                ?.mapNotNull { el ->
                    (el as? JsonObject)
                        ?.get("duration")
                        ?.jsonPrimitive
                        ?.content
                        ?.toDoubleOrNull()
                        ?.toInt()
                }?.maxOrNull() ?: 0

        if (maxFormatDuration > 0) return maxFormatDuration

        // 4. Give up
        return numericDuration
    }

    /**
     * Parses a yt-dlp `duration_string` value into whole seconds.
     * Accepts `"H:MM:SS"`, `"M:SS"`, `"SS"` and decimal variants.
     */
    private fun parseDurationString(s: String): Int {
        val parts = s.trim().split(":").map { it.toDoubleOrNull() ?: return 0 }
        return when (parts.size) {
            1 -> parts[0].toInt()
            2 -> (parts[0] * 60 + parts[1]).toInt()
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]).toInt()
            else -> 0
        }
    }

}
