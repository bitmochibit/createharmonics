package me.mochibit.createharmonics.audio.info

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.mochibit.createharmonics.audio.process.FFprobeExecutor
import me.mochibit.createharmonics.audio.process.YTdlpExecutor
import me.mochibit.createharmonics.foundation.err
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * FFMPEG ready audio data
 */
data class AudioInfo(
    val audioUrl: String,
    val durationSeconds: Int,
    val title: String,
    val sampleRate: Float,
    val isLive: Boolean,
    val httpHeaders: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(), // Default to creation time
) {
    companion object {
        suspend fun withYtdlp(rawUrl: String): AudioInfo =
            AudioInfoCache.getAudioInfo(rawUrl, AudioInfoCache.InfoExtractionStrategy.Companion.YtdlpStrat)

        suspend fun withFFProbe(rawUrl: String): AudioInfo =
            AudioInfoCache.getAudioInfo(rawUrl, AudioInfoCache.InfoExtractionStrategy.Companion.FFProbeStrat)
    }
}

/**
 * [AudioInfo] Cache
 */
object AudioInfoCache {
    private val cache = ConcurrentHashMap<String, AudioInfo>()
    private val CACHE_TTL_MS = 15.minutes.inWholeMilliseconds
    private val ytdlpWrapper: YTdlpExecutor by lazy { YTdlpExecutor() }
    private val ffprobeWrapper: FFprobeExecutor by lazy { FFprobeExecutor() }

    interface InfoExtractionStrategy {
        suspend fun extract(rawUrl: String): AudioInfo

        companion object {
            object YtdlpStrat : InfoExtractionStrategy {
                override suspend fun extract(rawUrl: String): AudioInfo = ytdlpWrapper.extractAudioInfo(rawUrl)
            }

            object FFProbeStrat : InfoExtractionStrategy {
                override suspend fun extract(rawUrl: String): AudioInfo = ffprobeWrapper.probe(rawUrl)
            }
        }
    }

    suspend fun getAudioInfo(
        url: String,
        strategy: InfoExtractionStrategy,
    ): AudioInfo {
        cache[url]?.let { entry ->
            if (!isUrlStillValid(entry.audioUrl)) {
                cache.remove(url)
                return@let
            }

            if (System.currentTimeMillis() - entry.timestamp >= CACHE_TTL_MS) {
                cache.remove(url)
            } else {
                return entry
            }
        }

        val extractedInfo = strategy.extract(url)
        return extractedInfo.also { cache[url] = it }
    }

    private fun isUrlStillValid(audioUrl: String): Boolean {
        return try {
            val expire =
                URI
                    .create(audioUrl)
                    .query
                    .split("&")
                    .firstOrNull { it.startsWith("expire=") }
                    ?.substringAfter("expire=")
                    ?.toLong()
                    ?: return true
            Instant.ofEpochSecond(expire).isAfter(Instant.now())
        } catch (e: Exception) {
            true
        }
    }

    fun clear() = cache.clear()

    fun size(): Int = cache.size
}
