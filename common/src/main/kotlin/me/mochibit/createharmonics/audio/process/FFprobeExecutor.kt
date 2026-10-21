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
import kotlin.coroutines.cancellation.CancellationException


data class ProbeResult(
    val durationSeconds: Int,
    val sampleRate: Float,
    val title: String?,
    val artist: String?,
    val isLive: Boolean,
)

object FFprobeExecutor {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun probe(url: String): AudioInfo {
        val r = probeRaw(url)
        return AudioInfo(
            audioUrl = url,
            durationSeconds = r.durationSeconds,
            title = r.title ?: fallbackTitle(url),
            sampleRate = r.sampleRate,
            isLive = r.isLive,
            artist = r.artist ?: "",
        )
    }

    /** [source] is a URL or an absolute local path. */
    suspend fun probeRaw(source: String): ProbeResult {
        try {
            val probePath = FFMPEGProvider.ffprobePath
                ?: error("ffprobe binary not found")

            val result = runProcess(
                listOf(
                    probePath,
                    "-v", "quiet",
                    "-print_format", "json",
                    "-select_streams", "a:0",
                    "-show_entries", "format=duration:format_tags:stream=sample_rate",
                    "-user_agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    source,
                ),
            )
            check(result.exitCode == 0 && result.stdout.isNotBlank()) {
                "process exited with code ${result.exitCode}"
            }

            val root = json.parseToJsonElement(result.stdout).jsonObject
            val format = root["format"]?.jsonObject ?: error("missing format data")

            val tags = format["tags"]?.jsonObject.orEmpty()
                .entries.associate { (k, v) -> k.lowercase() to v.jsonPrimitive.content }

            val durationRaw = format["duration"]?.jsonPrimitive?.content?.toDoubleOrNull()
            val sampleRate = root["streams"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("sample_rate")?.jsonPrimitive?.content?.toFloatOrNull() ?: 48_000f

            return ProbeResult(
                durationSeconds = durationRaw?.toInt() ?: 0,
                sampleRate = sampleRate,
                title = tags["title"]?.takeIf { it.isNotBlank() },
                artist = tags["artist"]?.takeIf { it.isNotBlank() },
                isLive = durationRaw == null || durationRaw == 0.0 || "icy-name" in tags,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("FFprobeExecutor: probe failed for $source: ${e.message}", e)
        }
    }

    private fun fallbackTitle(url: String): String =
        url.substringBefore('?').substringBefore('#')
            .substringAfterLast('/').substringAfterLast('\\')
            .ifBlank { "Unknown" }
}