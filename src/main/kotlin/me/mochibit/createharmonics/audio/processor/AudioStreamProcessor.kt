package me.mochibit.createharmonics.audio.processor

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.mochibit.createharmonics.Logger.err
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.audio.binProvider.FFMPEG
import me.mochibit.createharmonics.audio.source.AudioSource
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Streamlined audio processor with efficient pipeline:
 * 1. YTDL gets audio URL (with caching)
 * 2. FFmpeg streams or downloads based on duration (>3min = download)
 * 3. Raw PCM data is streamed without processing
 * 4. Effects are applied on-demand in BufferedAudioStream during playback
 */
class AudioStreamProcessor(
    private val sampleRate: Int = 48000
) {
    companion object {
        private const val DURATION_THRESHOLD_SECONDS = 180 // 3 minutes
        private const val CHUNK_SIZE = 8192
        private val DOWNLOAD_DIR = Paths.get("downloaded_audio")
    }

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
        if (!FFMPEG.isAvailable()) {
            info("FFmpeg not found, installing...")
            if (!FFMPEG.install()) {
                err("Failed to install FFmpeg")
                throw IllegalStateException("FFMPEG installation failed")
            }
            info("FFmpeg installed successfully")
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
                    // Invalidate the cache for this URL
                    me.mochibit.createharmonics.audio.AudioUrlCache.invalidate(identifier)
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
            decodeToStream(audioUrl)
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
                downloadAudio(audioUrl, audioFile)
                info("Download completed: ${audioFile.length()} bytes")
            } else {
                info("Using cached audio file: ${audioFile.absolutePath}")
            }

            // Process from local file - stream directly
            info("Starting FFmpeg decode from file")
            decodeFileToStream(audioFile)
                .collect { chunk ->
                    emit(chunk)
                }
        } catch (e: Exception) {
            err("Error processing downloaded audio: ${e.message}")
            throw e
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Download audio from URL to local file using FFmpeg.
     */
    private suspend fun downloadAudio(audioUrl: String, outputFile: File) = withContext(Dispatchers.IO) {
        val ffmpegPath = FFMPEG.getExecutablePath()
            ?: throw IllegalStateException("FFmpeg not found")

        // Delete partial downloads if they exist
        if (outputFile.exists()) {
            outputFile.delete()
        }

        // Use stream copy for faster downloads (no re-encoding)
        val command = listOf(
            ffmpegPath,
            "-reconnect", "1",
            "-reconnect_streamed", "1",
            "-reconnect_delay_max", "5",
            "-i", audioUrl,
            "-vn", // No video
            "-c:a", "copy", // Copy audio stream without re-encoding
            "-y", // Overwrite output file without asking
            "-loglevel", "warning",
            outputFile.absolutePath
        )

        info("FFmpeg download command: ${command.joinToString(" ")}")
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        // Monitor output asynchronously
        val outputJob = launch {
            process.inputStream.bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    if (line.isNotBlank()) {
                        info("FFmpeg: $line")
                    }
                }
            }
        }

        val exitCode = process.waitFor()
        outputJob.cancel()

        if (exitCode != 0) {
            // Clean up failed download
            if (outputFile.exists()) {
                outputFile.delete()
            }
            err("FFmpeg download failed with exit code: $exitCode")
            throw IllegalStateException("FFmpeg download failed with exit code: $exitCode")
        }

        // Validate the downloaded file
        if (!outputFile.exists() || outputFile.length() == 0L) {
            err("Downloaded file is missing or empty")
            throw IllegalStateException("Downloaded file validation failed")
        }

        info("FFmpeg download completed successfully: ${outputFile.length()} bytes")
    }

    /**
     * Decode audio URL directly to PCM stream.
     */
    private fun decodeToStream(audioUrl: String): Flow<ByteArray> = flow {
        val ffmpegPath = FFMPEG.getExecutablePath()
            ?: throw IllegalStateException("FFmpeg not found")

        val command = listOf(
            ffmpegPath,
            "-reconnect", "1",
            "-reconnect_streamed", "1",
            "-reconnect_delay_max", "5",
            "-i", audioUrl,
            "-f", "s16le",
            "-ar", sampleRate.toString(),
            "-ac", "1",
            "-loglevel", "warning",
            "pipe:1"
        )

        info("Starting FFmpeg stream: ${command.joinToString(" ")}")
        val process = ProcessBuilder(command)
            .redirectErrorStream(false)
            .start()

        // Monitor stderr for errors
        val errorJob = CoroutineScope(Dispatchers.IO).launch {
            process.errorStream.bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    if (line.isNotBlank()) {
                        err("FFmpeg: $line")
                    }
                }
            }
        }

        try {
            val buffer = ByteArray(CHUNK_SIZE)
            var totalBytes = 0L

            process.inputStream.use { input ->
                while (true) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break

                    emit(buffer.copyOf(bytesRead))
                    totalBytes += bytesRead
                }
            }

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                err("FFmpeg process exited with code: $exitCode")
            }
            info("Decoded $totalBytes bytes of PCM")
        } finally {
            errorJob.cancel()
            process.destroy()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Decode audio file directly to PCM stream.
     */
    private fun decodeFileToStream(audioFile: File): Flow<ByteArray> = flow {
        val ffmpegPath = FFMPEG.getExecutablePath()
            ?: throw IllegalStateException("FFmpeg not found")

        val command = listOf(
            ffmpegPath,
            "-i", audioFile.absolutePath,
            "-f", "s16le",
            "-ar", sampleRate.toString(),
            "-ac", "1",
            "-loglevel", "error",
            "pipe:1"
        )

        val process = ProcessBuilder(command).start()
        try {
            val buffer = ByteArray(CHUNK_SIZE)
            var totalBytes = 0L

            process.inputStream.use { input ->
                while (true) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break

                    emit(buffer.copyOf(bytesRead))
                    totalBytes += bytesRead
                }
            }

            info("Decoded $totalBytes bytes of PCM from file")
        } finally {
            process.destroy()
        }
    }.flowOn(Dispatchers.IO)
}