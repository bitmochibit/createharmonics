package me.mochibit.createharmonics.audio.processor

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.mochibit.createharmonics.Logger.err
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.audio.cache.YoutubeCache
import me.mochibit.createharmonics.audio.process.FFmpegExecutor
import me.mochibit.createharmonics.audio.source.AudioSource
import java.nio.file.Files
import java.nio.file.Paths


class AudioStreamProcessor(
    private val sampleRate: Int = 48000
) {
    companion object {
        private const val DURATION_THRESHOLD_SECONDS = 180 // 3 minutes
        private val DOWNLOAD_DIR = Paths.get("downloaded_audio")
    }

    private val ffmpegWrapper = FFmpegExecutor()

    init {
        // Ensure download directory exists
        Files.createDirectories(DOWNLOAD_DIR)
    }

    /**
     * Main entry point: creates an audio stream with raw PCM data.
     * Effects will be applied on-demand during playback via the EffectChain.
     */
    fun processAudioStream(
        audioSource: AudioSource
    ): Flow<ByteArray> = flow {
        // Ensure FFmpeg is installed before processing
        if (!ffmpegWrapper.ensureInstalled()) {
            err("Failed to ensure FFmpeg installation")
            throw IllegalStateException("FFMPEG installation failed")
        }

        // Get the original identifier (YouTube URL or other source)
        val identifier = audioSource.getIdentifier()

        // Step 1: Get duration to determine streaming strategy
        info("Getting audio info for: $identifier")
        val duration = audioSource.getDurationSeconds()
        info("Audio duration: ${duration}s")

        // Step 2: Determine if we should stream or download
        // Download SHORT songs (<3min) for caching, stream LONG songs (>3min)
        val shouldDownload = duration <= DURATION_THRESHOLD_SECONDS

        // Retry logic for expired URLs (common with YouTube)
        var attempt = 0
        val maxAttempts = 2
        var lastError: Exception? = null

        while (attempt < maxAttempts) {
            try {
                if (shouldDownload) {
                    info("Audio duration <= 3 minutes, downloading for caching (attempt ${attempt + 1}/$maxAttempts)")
                    // Resolve URL just-in-time before download
                    val audioUrl = audioSource.resolveAudioUrl()
                    processWithDownload(audioUrl, identifier)
                        .collect { chunk -> emit(chunk) }
                } else {
                    info("Audio duration > 3 minutes, streaming directly (attempt ${attempt + 1}/$maxAttempts)")
                    // Resolve URL just-in-time before streaming
                    val audioUrl = audioSource.resolveAudioUrl()
                    processWithStreaming(audioUrl)
                        .collect { chunk -> emit(chunk) }
                }
                // Success - break out of retry loop
                return@flow
            } catch (e: Exception) {
                lastError = e
                attempt++

                // Check if it's a 403 error (expired URL)
                val is403Error = e.message?.contains("403") == true ||
                        e.message?.contains("Forbidden") == true ||
                        e.message?.contains("HTTP error") == true

                if (is403Error && attempt < maxAttempts) {
                    err("Detected expired URL (403 error), invalidating cache and retrying...")
                    // Invalidate the cache for this URL (YouTube-specific)
                    YoutubeCache.invalidate(identifier)
                    delay(500) // Brief delay before retry
                } else if (attempt >= maxAttempts) {
                    err("Max retry attempts reached, giving up")
                    throw e
                } else {
                    // Non-403 error, don't retry
                    throw e
                }
            }
        }

        // If we get here, all retries failed
        throw lastError ?: IllegalStateException("Failed to process audio after $maxAttempts attempts")
    }.flowOn(Dispatchers.IO)

    /**
     * Stream audio directly from URL to output.
     */
    private fun processWithStreaming(
        audioUrl: String
    ): Flow<ByteArray> = flow {
        info("Starting FFmpeg decode (streaming)")
        try {
            ffmpegWrapper.decodeUrlToStream(audioUrl, sampleRate)
                .collect { chunk ->
                    emit(chunk)
                }
            info("FFmpeg decode completed")
        } catch (e: Exception) {
            err("Decode error: ${e.message}")
            throw e
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Download audio to file first, then process from file.
     */
    private fun processWithDownload(
        audioUrl: String,
        identifier: String
    ): Flow<ByteArray> = flow {
        // Create unique filename with proper audio extension
        val sanitizedName = identifier.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val audioFile = DOWNLOAD_DIR.resolve("${sanitizedName}.opus").toFile()

        try {
            // Download if not already cached
            if (!audioFile.exists()) {
                info("Downloading audio to: ${audioFile.absolutePath}")
                val result = ffmpegWrapper.downloadAudio(audioUrl, audioFile)
                when (result) {
                    is FFmpegExecutor.FFmpegResult.Success -> {
                        info("Download completed: ${result.bytesProcessed} bytes")
                    }
                    is FFmpegExecutor.FFmpegResult.Error -> {
                        err("Download failed: ${result.message}")
                        throw IllegalStateException("Download failed: ${result.message}")
                    }
                }
            } else {
                info("Using cached audio file: ${audioFile.absolutePath}")
            }

            // Process from local file - stream directly
            info("Starting FFmpeg decode from file")
            ffmpegWrapper.decodeFileToStream(audioFile, sampleRate)
                .collect { chunk ->
                    emit(chunk)
                }
        } catch (e: Exception) {
            err("Error processing downloaded audio: ${e.message}")
            throw e
        }
    }.flowOn(Dispatchers.IO)
}