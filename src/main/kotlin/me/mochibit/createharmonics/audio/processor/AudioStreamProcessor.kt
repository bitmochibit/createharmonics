package me.mochibit.createharmonics.audio.processor

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.mochibit.createharmonics.Logger.err
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.audio.pcm.PitchFunction
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
 * 3. Data flows through channels to ring buffer
 * 4. Raw PCM data is streamed without processing
 * 5. Pitch shifting is applied on-demand during playback
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
     * Pitch shifting will be applied on-demand during playback.
     */
    fun processAudioStream(
        audioSource: AudioSource,
        pitchFunction: PitchFunction
    ): Flow<ByteArray> = channelFlow {
        // Ensure FFmpeg is installed before processing
        if (!FFMPEG.isAvailable()) {
            info("FFmpeg not found, installing...")
            if (!FFMPEG.install()) {
                err("Failed to install FFmpeg")
                throw IllegalStateException("FFMPEG installation failed")
            }
            info("FFmpeg installed successfully")
        }

        // Step 1: Resolve audio URL (cached by YTDL)
        info("Resolving audio URL for: ${audioSource.getIdentifier()}")
        val audioUrl = audioSource.resolveAudioUrl()
        val duration = audioSource.getDurationSeconds()
        info("Audio URL resolved, duration: ${duration}s")

        // Step 2: Determine if we should stream or download
        val shouldDownload = duration > DURATION_THRESHOLD_SECONDS

        if (shouldDownload) {
            info("Audio duration > 3 minutes, downloading to local file")
            processWithDownload(audioUrl, audioSource.getIdentifier())
                .collect { chunk -> send(chunk) }
        } else {
            info("Audio duration <= 3 minutes, streaming directly")
            processWithStreaming(audioUrl)
                .collect { chunk -> send(chunk) }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Stream audio directly from URL through buffer to output.
     */
    private fun processWithStreaming(
        audioUrl: String
    ): Flow<ByteArray> = flow {
        val ringBuffer = PCMRingBuffer(capacity = sampleRate * 10) // 10 seconds buffer

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

            // Job 2: Read raw PCM from buffer and stream it (no pitch shifting here)
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
    ): Flow<ByteArray> = channelFlow {
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

            // Process from local file
            val ringBuffer = PCMRingBuffer(capacity = sampleRate * 10)

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

                // Stream raw PCM from buffer (no pitch shifting)
                val streamJob = launch(Dispatchers.IO) {
                    try {
                        streamRawPcmFromBuffer(ringBuffer)
                            .collect { chunk -> send(chunk) }
                    } catch (e: Exception) {
                        err("Streaming error: ${e.message}")
                        throw e
                    }
                }

                streamJob.join()
                decodeJob.cancel()
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

        // Use re-encoding instead of copy to ensure compatibility with YouTube URLs
        val command = listOf(
            ffmpegPath,
            "-reconnect", "1",
            "-reconnect_streamed", "1",
            "-reconnect_delay_max", "5",
            "-i", audioUrl,
            "-vn", // No video
            "-acodec", "libopus", // Use Opus codec for better compression
            "-b:a", "128k", // Audio bitrate
            "-y", // Overwrite output file without asking
            "-loglevel", "warning",
            outputFile.absolutePath
        )

        info("FFmpeg download command: ${command.joinToString(" ")}")
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        // Capture output for debugging
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            err("FFmpeg download error output: $output")
            throw IllegalStateException("FFmpeg download failed with exit code: $exitCode. Output: $output")
        }

        info("FFmpeg download completed successfully")
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
            "-i", audioUrl,
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

        inputStream.use { input ->
            while (isActive) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) break

                // Convert bytes to samples and write to ring buffer
                val samples = PCMUtils.bytesToShorts(buffer.copyOf(bytesRead))
                var offset = 0
                while (offset < samples.size && isActive) {
                    val written = ringBuffer.write(samples, offset, samples.size - offset)
                    offset += written
                    if (written == 0) {
                        delay(10) // Small delay if buffer is full
                    }
                }

                totalBytes += bytesRead
            }
        }

        info("Decoded $totalBytes bytes of PCM to buffer")
    }

    /**
     * Stream raw PCM data from ring buffer without any processing.
     * Pitch shifting will be applied on-demand during playback.
     */
    private fun streamRawPcmFromBuffer(
        ringBuffer: PCMRingBuffer
    ): Flow<ByteArray> = flow {
        info("Starting raw PCM streaming, sample rate: $sampleRate")

        try {
            // Wait for initial buffer to fill (at least 1 second of audio)
            info("Waiting for initial buffer fill...")
            ringBuffer.waitForSamples(sampleRate, timeoutMs = 5000)
            info("Initial buffer ready: ${ringBuffer.availableCount()} samples available")

            val chunkSizeSamples = 16800 // ~350ms of audio at 48kHz
            var totalSamplesStreamed = 0
            var emptyReadAttempts = 0
            val maxEmptyAttempts = 50 // Allow 50 * 100ms = 5 seconds of waiting
            var lastLoggedBuffer = 0

            while (true) {
                val availableInBuffer = ringBuffer.availableCount()

                // If buffer is empty and complete, we're done
                if (availableInBuffer == 0 && ringBuffer.isComplete) {
                    info("Buffer exhausted and complete, finishing streaming")
                    break
                }

                // Wait if buffer is too low (unless we're at the end)
                if (availableInBuffer < chunkSizeSamples / 2 && !ringBuffer.isComplete) {
                    // Use longer timeout to avoid premature exit
                    info("Buffer low: ${availableInBuffer} samples, waiting for more (isComplete=${ringBuffer.isComplete})")
                    val waitSuccess = ringBuffer.waitForSamples(chunkSizeSamples / 4, timeoutMs = 1000)
                    if (!waitSuccess && !ringBuffer.isComplete) {
                        // Still waiting for data, continue looping
                        info("Wait timeout - buffer: ${ringBuffer.availableCount()} samples, isComplete=${ringBuffer.isComplete}")
                    }
                }

                // Read what's available
                val samplesToRead = minOf(chunkSizeSamples, ringBuffer.availableCount())
                if (samplesToRead == 0) {
                    if (ringBuffer.isComplete) {
                        info("Ring buffer complete with no remaining samples")
                        break
                    }
                    // No data but not complete - wait before retry
                    emptyReadAttempts++
                    info("Empty read attempt ${emptyReadAttempts}/${maxEmptyAttempts}: buffer=${ringBuffer.availableCount()}, isComplete=${ringBuffer.isComplete}")
                    if (emptyReadAttempts >= maxEmptyAttempts) {
                        err("Ring buffer stuck: no data for ${maxEmptyAttempts * 100}ms, isComplete=${ringBuffer.isComplete}")
                        err("Total samples streamed before stuck: ${totalSamplesStreamed} (${totalSamplesStreamed / sampleRate}s)")
                        break
                    }
                    Thread.sleep(100)
                    continue
                }

                val samples = ShortArray(samplesToRead)
                val actualRead = ringBuffer.read(samples, 0, samplesToRead)

                if (actualRead == 0) {
                    if (ringBuffer.isComplete) {
                        info("Ring buffer complete, read returned 0")
                        break
                    }
                    emptyReadAttempts++
                    info("Read returned 0 - attempt ${emptyReadAttempts}/${maxEmptyAttempts}")
                    if (emptyReadAttempts >= maxEmptyAttempts) {
                        err("Ring buffer read failed repeatedly, ending stream")
                        break
                    }
                    continue
                }

                // Reset empty attempts on successful read
                emptyReadAttempts = 0

                // Convert to bytes and emit (no pitch shifting)
                val bytes = PCMUtils.shortsToBytes(samples.copyOf(actualRead))
                emit(bytes)

                totalSamplesStreamed += actualRead

                // Log progress every 5 seconds OR when buffer changes significantly
                val bufferSeconds = ringBuffer.availableCount().toDouble() / sampleRate
                if (totalSamplesStreamed % (sampleRate * 5) < chunkSizeSamples ||
                    Math.abs(ringBuffer.availableCount() - lastLoggedBuffer) > sampleRate * 2) {
                    info("Raw PCM streaming: ${totalSamplesStreamed / sampleRate}s streamed, buffer: ${String.format("%.1f", bufferSeconds)}s, isComplete=${ringBuffer.isComplete}")
                    lastLoggedBuffer = ringBuffer.availableCount()
                }
            }

            info("Raw PCM streaming completed: $totalSamplesStreamed samples (${totalSamplesStreamed / sampleRate}s)")
        } catch (e: Exception) {
            err("Raw PCM streaming error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}
