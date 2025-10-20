package me.mochibit.createharmonics.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.audio.pcm.PitchFunction
import me.mochibit.createharmonics.audio.pcm.PCMProcessor
import me.mochibit.createharmonics.audio.pcm.PCMUtils
import me.mochibit.createharmonics.audio.processor.AudioStreamProcessor
import me.mochibit.createharmonics.audio.source.AudioSource
import me.mochibit.createharmonics.coroutine.ModCoroutineManager
import net.minecraft.resources.ResourceLocation
import java.io.InputStream
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Buffered input stream that processes audio with real-time on-demand pitch shifting.
 * Raw PCM data is buffered, and pitch shifting is applied when data is read.
 */
class BufferedAudioStream(
    private val audioSource: AudioSource,
    private val pitchFunction: PitchFunction,
    private val sampleRate: Int,
    private val resourceLocation: ResourceLocation,
    private val processor: AudioStreamProcessor
) : InputStream() {
    // Queue of raw PCM samples (not pitch-shifted)
    private val rawSampleQueue = ConcurrentLinkedQueue<Short>()

    // Maximum queue size (10 seconds of audio) - increased for better buffering
    private val maxQueueSize = sampleRate * 10

    // Output buffer for pitch-shifted data
    private var outputBuffer: ByteArray? = null
    private var outputPosition = 0

    private var streamJob: Job? = null

    @Volatile
    private var finished = false

    @Volatile
    private var error: Exception? = null

    @Volatile
    private var preBuffered = false

    private val preBufferLatch = java.util.concurrent.CountDownLatch(1)

    // Track playback time for pitch function
    private var samplesRead = 0L
    private var lastPitch = 0f

    // Minimum samples to keep in buffer before processing
    private val minBufferSamples = sampleRate / 20 // 50ms - reduced for faster response

    // Flag to track if we've logged the stream end
    @Volatile
    private var hasLoggedEnd = false

    init {
        startPipeline()
    }

    private fun startPipeline() {
        streamJob = ModCoroutineManager.launch(Dispatchers.IO) {
            try {
                Logger.info("Starting audio pipeline for: ${audioSource.getIdentifier()}")

                var chunkCount = 0
                processor.processAudioStream(audioSource, pitchFunction)
                    .onEach { chunk ->
                        // Convert bytes to samples
                        val samples = PCMUtils.bytesToShorts(chunk)

                        // Apply backpressure: wait if queue is too full
                        while (rawSampleQueue.size + samples.size > maxQueueSize && error == null) {
                            kotlinx.coroutines.delay(10)
                        }

                        // Add to queue
                        samples.forEach { rawSampleQueue.offer(it) }
                        chunkCount++

                        // Mark as pre-buffered after receiving first chunk
                        if (!preBuffered && chunkCount >= 1) {
                            preBuffered = true
                            preBufferLatch.countDown()
                            Logger.info("Pre-buffering completed: $chunkCount chunk(s) received, ${rawSampleQueue.size} samples buffered")
                        }

                        // Log queue size periodically
                        if (chunkCount % 10 == 0) {
                            Logger.info("Raw queue status: ${rawSampleQueue.size} samples (${rawSampleQueue.size.toDouble() / sampleRate}s)")
                        }
                    }
                    .catch { e ->
                        error = e as? Exception ?: Exception(e)
                        Logger.err("Pipeline error: ${e.message}")
                        e.printStackTrace()
                        preBufferLatch.countDown()
                    }
                    .onCompletion {
                        finished = true
                        Logger.info("Pipeline finished, final queue size: ${rawSampleQueue.size}")
                        preBufferLatch.countDown()
                    }
                    .collect()
            } catch (e: Exception) {
                error = e
                Logger.err("Pipeline error: ${e.message}")
                e.printStackTrace()
                finished = true
                preBufferLatch.countDown()
            }
        }
    }

    override fun read(): Int {
        val b = ByteArray(1)
        val result = read(b, 0, 1)
        return if (result == -1) -1 else b[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0

        // Wait for pre-buffering on first read
        waitForPreBuffer()

        // Check for errors
        error?.let { throw it }

        var totalRead = 0
        var consecutiveFailures = 0
        val maxConsecutiveFailures = 100 // ~1 second timeout (100 * 10ms)
        var totalWaitCycles = 0
        val maxTotalWaitCycles = 500 // ~5 seconds total timeout before giving up

        // BLOCKING: Keep trying until we have data or stream is finished
        while (totalRead < len) {
            // If we have output buffer, read from it
            if (outputBuffer != null && outputPosition < outputBuffer!!.size) {
                val available = outputBuffer!!.size - outputPosition
                val toRead = minOf(len - totalRead, available)

                System.arraycopy(outputBuffer!!, outputPosition, b, off + totalRead, toRead)
                outputPosition += toRead
                totalRead += toRead

                if (outputPosition >= outputBuffer!!.size) {
                    outputBuffer = null
                    outputPosition = 0
                }

                consecutiveFailures = 0 // Reset on success
                continue
            }

            // Try to process more data
            val processed = processNextChunk()

            if (!processed) {
                // Check if stream is truly done
                if (finished && rawSampleQueue.isEmpty()) {
                    if (!hasLoggedEnd) {
                        Logger.info("Stream ended: no more data available (read ${samplesRead} samples total)")
                        hasLoggedEnd = true
                    }
                    // Return what we have, or -1 if nothing
                    return if (totalRead > 0) totalRead else -1
                }

                // Not finished yet - check timeout
                consecutiveFailures++
                totalWaitCycles++

                if (consecutiveFailures >= maxConsecutiveFailures) {
                    // We've waited 1 second - check if we have ANY data in queue
                    val queueSize = rawSampleQueue.size

                    if (queueSize > 0) {
                        // Force process whatever we have, even if below minimum
                        Logger.info("Timeout - force processing ${queueSize} samples (below minimum threshold)")
                        if (processRemainingData(queueSize)) {
                            consecutiveFailures = 0
                            continue
                        }
                    }

                    if (totalRead > 0) {
                        // Return partial data to avoid complete hang
                        Logger.info("Timeout waiting for data, returning ${totalRead} bytes")
                        return totalRead
                    } else if (finished) {
                        // Pipeline finished but no data - truly done
                        Logger.info("Stream finished with no remaining data after timeout")
                        return -1
                    } else {
                        // Check if pipeline is stuck (total wait time exceeded)
                        if (totalWaitCycles >= maxTotalWaitCycles) {
                            Logger.err("Pipeline appears stuck: finished=$finished, queue=${queueSize}, waited ${totalWaitCycles * 10}ms total")
                            Logger.err("Forcing stream end to prevent infinite hang")
                            finished = true
                            return -1
                        }

                        // Still waiting for pipeline, log and continue (but don't reset total counter)
                        Logger.info("Timeout waiting for data: finished=$finished, queue=${queueSize}, totalWait=${totalWaitCycles * 10}ms")
                        consecutiveFailures = 0 // Reset consecutive but keep total count
                    }
                }

                // Stream not finished, wait for more data
                // CRITICAL: We must block here, not return 0, or Minecraft stops playback
                Thread.sleep(10)
                continue
            }

            // Successfully processed data
            consecutiveFailures = 0
        }

        return totalRead
    }

    /**
     * Process a chunk of raw samples with on-demand pitch shifting.
     * Returns true if data was processed, false if no more data available.
     * NON-BLOCKING: Returns immediately if insufficient data.
     */
    private fun processNextChunk(): Boolean {
        // Quick check: do we have minimum samples?
        if (rawSampleQueue.size < minBufferSamples) {
            // If stream is finished, process whatever we have left
            if (finished && rawSampleQueue.isNotEmpty()) {
                // Process remaining samples
                val remainingSamples = rawSampleQueue.size
                if (remainingSamples > 0) {
                    return processRemainingData(remainingSamples)
                }
            }
            return false
        }

        // Determine chunk size to process (adaptive based on available data)
        val availableData = rawSampleQueue.size
        val chunkSize = when {
            availableData > sampleRate -> sampleRate / 4 // 0.25s if we have plenty
            availableData > sampleRate / 2 -> sampleRate / 8 // 0.125s if moderate
            else -> minOf(sampleRate / 20, availableData) // 50ms minimum
        }

        if (chunkSize == 0) return false

        // Extract samples from queue
        val inputSamples = ShortArray(chunkSize) {
            rawSampleQueue.poll() ?: 0
        }

        // Get current pitch for this chunk
        val currentTime = samplesRead.toDouble() / sampleRate
        val currentPitch = pitchFunction.getPitchAt(currentTime)

        // Log pitch changes and buffer status
        if (currentPitch != lastPitch) {
            val bufferTimeSeconds = rawSampleQueue.size.toDouble() / sampleRate
            Logger.info("Applying pitch on-demand: $lastPitch -> $currentPitch at ${String.format("%.2f", currentTime)}s (buffer: ${rawSampleQueue.size} samples = ${String.format("%.1f", bufferTimeSeconds)}s)")
            lastPitch = currentPitch
        }

        // Apply pitch shifting
        val outputSamples = PCMProcessor.pitchShift(inputSamples, currentPitch)

        // Convert to bytes and store in output buffer
        outputBuffer = PCMUtils.shortsToBytes(outputSamples)
        outputPosition = 0

        // Update read counter based on INPUT samples consumed
        samplesRead += inputSamples.size

        return true
    }

    /**
     * Process remaining data at the end of the stream.
     */
    private fun processRemainingData(sampleCount: Int): Boolean {
        if (sampleCount == 0) return false

        val inputSamples = ShortArray(sampleCount) {
            rawSampleQueue.poll() ?: 0
        }

        val currentTime = samplesRead.toDouble() / sampleRate
        val currentPitch = pitchFunction.getPitchAt(currentTime)

        Logger.info("Processing final ${sampleCount} samples at pitch $currentPitch")

        val outputSamples = PCMProcessor.pitchShift(inputSamples, currentPitch)
        outputBuffer = PCMUtils.shortsToBytes(outputSamples)
        outputPosition = 0

        samplesRead += inputSamples.size

        return true
    }

    private fun waitForPreBuffer() {
        if (!preBuffered) {
            try {
                Logger.info("Waiting for pre-buffering...")
                if (!preBufferLatch.await(30, java.util.concurrent.TimeUnit.SECONDS)) {
                    Logger.err("Pre-buffering timeout! No audio data received after 30 seconds")
                    error?.let { throw it }
                    throw java.io.IOException("Pre-buffering timeout - no audio data received")
                }
                Logger.info("Pre-buffering wait completed")
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw java.io.IOException("Pre-buffering interrupted", e)
            }
        }
    }

    override fun close() {
        streamJob?.cancel()
        rawSampleQueue.clear()
        outputBuffer = null
        StreamRegistry.unregisterStream(resourceLocation)
        super.close()
    }
}
