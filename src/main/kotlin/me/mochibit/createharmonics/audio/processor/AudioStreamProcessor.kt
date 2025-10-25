package me.mochibit.createharmonics.audio.processor

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.mochibit.createharmonics.Logger.err
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.audio.binProvider.FFMPEG
import me.mochibit.createharmonics.audio.pcm.PCMRingBuffer
import me.mochibit.createharmonics.audio.pcm.PCMUtils
import me.mochibit.createharmonics.audio.source.AudioSource
import java.io.File
import java.io.InputStream
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
        private val DOWNLOAD_DIR = Paths.get("run", "downloaded_audio")
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
     * Stream audio directly from URL through buffer to output.
     */
    private fun processWithStreaming(
        audioUrl: String
    ): Flow<ByteArray> = flow {
        val ringBuffer = PCMRingBuffer(capacity = (sampleRate * 0.2).toInt()) // 200ms buffer for low latency

        coroutineScope {
            // Job 1: FFmpeg decodes URL to PCM and fills ring buffer (fast as possible)
            val decodeJob = launch(Dispatchers.IO) {
                try {
                    info("Starting FFmpeg decode to buffer (streaming)")
                    decodeUrlToBuffer(audioUrl, ringBuffer)
                    ringBuffer.markComplete()
                    info("FFmpeg decode completed")
                } catch (e: Exception) {
                    err("Decode error: ${e.message}")
                    ringBuffer.markComplete()
                    throw e
                }
            }

            // Job 2: Read raw PCM from buffer and stream it (no processing here)
            try {
                info("Starting raw PCM streaming from buffer")
                streamRawPcmFromBuffer(ringBuffer)
                    .collect { chunk ->
                        emit(chunk)
                    }
                info("Raw PCM streaming completed")
            } catch (e: Exception) {
                err("Streaming error: ${e.message}")
                e.printStackTrace()
                throw e
            } finally {
                decodeJob.cancel()
            }
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

            // Process from local file - use much larger buffer since we're reading from disk
            // Use 10 seconds of buffer to prevent FFmpeg from blocking
            val ringBuffer = PCMRingBuffer(capacity = (sampleRate * 10.0).toInt()) // 10 second buffer

            coroutineScope {
                // Decode from file to buffer
                val decodeJob = launch(Dispatchers.IO) {
                    try {
                        info("Starting FFmpeg decode from file to buffer")
                        decodeFileToBuffer(audioFile, ringBuffer)
                        ringBuffer.markComplete()
                        info("FFmpeg decode completed")
                    } catch (e: Exception) {
                        err("Decode error: ${e.message}")
                        ringBuffer.markComplete()
                        throw e
                    }
                }

                // Stream raw PCM from buffer and emit directly
                try {
                    streamRawPcmFromBuffer(ringBuffer)
                        .collect { chunk ->
                            emit(chunk)
                        }
                } catch (e: Exception) {
                    err("Streaming error: ${e.message}")
                    throw e
                } finally {
                    decodeJob.cancel()
                }
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
     * Decode audio URL to PCM samples and write to ring buffer.
     */
    private suspend fun decodeUrlToBuffer(
        audioUrl: String,
        ringBuffer: PCMRingBuffer
    ) = withContext(Dispatchers.IO) {
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
        val errorJob = launch {
            process.errorStream.bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    if (line.isNotBlank()) {
                        err("FFmpeg: $line")
                    }
                }
            }
        }

        try {
            streamPcmToBuffer(process.inputStream, ringBuffer)
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                err("FFmpeg process exited with code: $exitCode")
            }
        } finally {
            errorJob.cancel()
            process.destroy()
        }
    }

    /**
     * Decode audio file to PCM samples and write to ring buffer.
     */
    private suspend fun decodeFileToBuffer(
        audioFile: File,
        ringBuffer: PCMRingBuffer
    ) = withContext(Dispatchers.IO) {
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
            streamPcmToBuffer(process.inputStream, ringBuffer)
        } finally {
            process.destroy()
        }
    }

    /**
     * Stream PCM data from input to ring buffer.
     */
    private suspend fun streamPcmToBuffer(
        inputStream: InputStream,
        ringBuffer: PCMRingBuffer
    ) = withContext(Dispatchers.IO) {
        val buffer = ByteArray(CHUNK_SIZE)
        var totalBytes = 0L
        var writeCount = 0

        info("Starting PCM stream to ring buffer")

        inputStream.use { input ->
            while (isActive) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) {
                    info("FFmpeg stream ended (EOF)")
                    break
                }

                // Convert bytes to samples and write to ring buffer
                val samples = PCMUtils.bytesToShorts(buffer.copyOf(bytesRead))
                var offset = 0
                while (offset < samples.size && isActive) {
                    val beforeWrite = System.currentTimeMillis()
                    val written = ringBuffer.write(samples, offset, samples.size - offset)
                    val writeTime = System.currentTimeMillis() - beforeWrite

                    if (writeTime > 100) {
                        info("Ring buffer write blocked for ${writeTime}ms (available: ${ringBuffer.availableCount()}/${(sampleRate * 10.0).toInt()})")
                    }

                    offset += written
                    if (written == 0) {
                        delay(10) // Small delay if buffer is full
                    }
                }

                totalBytes += bytesRead
                writeCount++

                // Log progress every 50 writes
                if (writeCount % 50 == 0) {
                    info("FFmpeg decode progress: $writeCount writes, $totalBytes bytes, buffer: ${ringBuffer.availableCount()} samples")
                }
            }
        }

        info("Decoded $totalBytes bytes ($writeCount writes) of PCM to buffer")
    }

    /**
     * Stream raw PCM data from ring buffer.
     */
    private fun streamRawPcmFromBuffer(ringBuffer: PCMRingBuffer): Flow<ByteArray> = flow {
        val tempBuffer = ShortArray(CHUNK_SIZE / 2)
        var totalEmitted = 0L
        var emitCount = 0

        while (true) {
            val samplesRead = ringBuffer.read(tempBuffer, 0, tempBuffer.size)

            if (samplesRead == 0) {
                if (ringBuffer.isComplete) {
                    info("Ring buffer streaming complete: emitted $emitCount chunks ($totalEmitted samples)")
                    break
                }
                delay(10)
                continue
            }

            val bytes = PCMUtils.shortsToBytes(tempBuffer.copyOf(samplesRead))
            emit(bytes)

            totalEmitted += samplesRead
            emitCount++

            // Log progress every 10 chunks
            if (emitCount % 10 == 0) {
                info("Ring buffer progress: emitted $emitCount chunks ($totalEmitted samples, ${totalEmitted.toDouble() / 48000}s), buffer available: ${ringBuffer.availableCount()}")
            }
        }
    }.flowOn(Dispatchers.IO)
}
