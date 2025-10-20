package me.mochibit.createharmonics.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.mochibit.createharmonics.Config
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
    companion object {
        // Pitch constraints - configurable limits
        // These are accessed from Config at runtime
        val MIN_PITCH: Float get() = Config.MIN_PITCH.get().toFloat()
        val MAX_PITCH: Float get() = Config.MAX_PITCH.get().toFloat()

        // Buffer size calculation based on pitch constraints
        val TARGET_PLAYBACK_BUFFER_SECONDS: Double get() = Config.PLAYBACK_BUFFER_SECONDS.get()
    }

    // Queue of raw PCM samples (not pitch-shifted)
    private val rawSampleQueue = ConcurrentLinkedQueue<Short>()

    // Maximum queue size - calculated for worst case (slowest pitch = 0.5)
    // At pitch 0.5, 5 seconds of playback requires 10 seconds of raw audio
    private val maxQueueSize: Int get() = (sampleRate * TARGET_PLAYBACK_BUFFER_SECONDS * (1.0 / MIN_PITCH)).toInt()

    // Output buffer for pitch-shifted data (pre-processed chunks)
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
    // This is calculated for worst-case scenario (highest pitch = 2.0)
    // At pitch 2.0, we consume samples twice as fast, so we need a bigger safety margin
    // We want at least 500ms of audio ready in the output buffer
    private val minBufferSamples: Int get() = (sampleRate * 0.5 * (1.0 / MIN_PITCH)).toInt() // 1 second worth at slowest pitch

    // Process larger chunks to reduce overhead and prevent main thread blocking
    // Aim for ~1 second of OUTPUT audio per processing call
    private val processChunkSize = sampleRate // 1 second of raw audio

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

                        Logger.info("Pipeline received chunk: ${chunk.size} bytes = ${samples.size} samples, queue before: ${rawSampleQueue.size}")

                        // Apply backpressure: wait if queue is too full
                        while (rawSampleQueue.size + samples.size > maxQueueSize && error == null && !finished) {
                            kotlinx.coroutines.delay(10)
                        }

                        // Add to queue
                        samples.forEach { rawSampleQueue.offer(it) }
                        chunkCount++

                        Logger.info("Pipeline added chunk, queue after: ${rawSampleQueue.size} samples")

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
                        // Don't treat cancellation as an error
                        if (e !is kotlinx.coroutines.CancellationException) {
                            error = e as? Exception ?: Exception(e)
                            Logger.err("Pipeline error: ${e.message}")
                            e.printStackTrace()
                        } else {
                            Logger.info("Pipeline cancelled gracefully")
                        }
                        finished = true
                        preBufferLatch.countDown()
                    }
                    .onCompletion { cause ->
                        finished = true
                        if (cause == null) {
                            Logger.info("Pipeline finished normally, final queue size: ${rawSampleQueue.size}")
                        } else if (cause is kotlinx.coroutines.CancellationException) {
                            Logger.info("Pipeline cancelled, final queue size: ${rawSampleQueue.size}")
                        } else {
                            Logger.err("Pipeline completed with error: ${cause.message}")
                        }
                        preBufferLatch.countDown()
                    }
                    .collect()
            } catch (e: Exception) {
                // Handle cancellation gracefully
                if (e is kotlinx.coroutines.CancellationException) {
                    Logger.info("Pipeline cancelled in catch block")
                } else {
                    error = e
                    Logger.err("Pipeline error: ${e.message}")
                    e.printStackTrace()
                }
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
        val maxConsecutiveFailures = 20 // ~200ms timeout (20 * 10ms) - reduced for faster response
        var totalWaitCycles = 0
        val maxTotalWaitCycles = 50 // ~500ms total timeout before giving up - much shorter

        // BLOCKING: Keep trying until we have data or stream is finished
        while (totalRead < len) {
            // Check if finished/cancelled early to avoid waiting
            if (finished && rawSampleQueue.isEmpty() && outputBuffer == null) {
                return if (totalRead > 0) totalRead else -1
            }

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
                totalWaitCycles = 0 // Reset total wait on success
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
                    // We've waited 200ms - check if we have ANY data in queue
                    val queueSize = rawSampleQueue.size

                    if (queueSize > 0) {
                        // Force process whatever we have, even if below minimum
                        if (processRemainingData(queueSize)) {
                            consecutiveFailures = 0
                            continue
                        }
                    }

                    if (totalRead > 0) {
                        // Return partial data immediately to avoid hang
                        return totalRead
                    } else if (finished) {
                        // Pipeline finished but no data - truly done
                        return -1
                    } else {
                        // Check if pipeline is stuck (total wait time exceeded)
                        if (totalWaitCycles >= maxTotalWaitCycles) {
                            Logger.info("Stream cancelled or stuck, ending playback (waited ${totalWaitCycles * 10}ms)")
                            finished = true
                            return if (totalRead > 0) totalRead else -1
                        }

                        // Reset consecutive but keep total count
                        consecutiveFailures = 0
                    }
                }

                // Stream not finished, wait for more data
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
                return processRemainingData(remainingSamples)
            }
            return false
        }

        // Use larger chunk size to reduce processing overhead
        // Process up to 1 second at a time, or whatever is available
        val availableData = rawSampleQueue.size
        val chunkSize = minOf(processChunkSize, availableData)

        if (chunkSize == 0) return false

        // Extract samples from queue
        val inputSamples = ShortArray(chunkSize) {
            rawSampleQueue.poll() ?: 0
        }

        // Get current pitch for this chunk
        val currentTime = samplesRead.toDouble() / sampleRate
        val currentPitch = pitchFunction.getPitchAt(currentTime).coerceIn(MIN_PITCH, MAX_PITCH)

        // Log pitch changes and buffer status
        if (currentPitch != lastPitch) {
            val bufferTimeSeconds = rawSampleQueue.size.toDouble() / sampleRate
            val playbackBufferSeconds = bufferTimeSeconds / currentPitch // Account for pitch affecting playback speed
            Logger.info("Applying pitch on-demand: $lastPitch -> $currentPitch at ${String.format("%.2f", currentTime)}s (raw buffer: ${String.format("%.1f", bufferTimeSeconds)}s, playback buffer: ${String.format("%.1f", playbackBufferSeconds)}s)")
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
        val currentPitch = pitchFunction.getPitchAt(currentTime).coerceIn(MIN_PITCH, MAX_PITCH)

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
        // Don't cancel the job or unregister here!
        // The stream might be reused by Minecraft's sound system
        // Only clean up the local resources
        Logger.info("BufferedAudioStream.close() called - clearing local buffers only")
        // Note: We intentionally don't cancel streamJob or unregister
        // The stream stays active until explicitly stopped by the jukebox
    }

    /**
     * Actually stop and clean up the stream.
     * This should be called when the jukebox is stopped/broken.
     */
    fun destroy() {
        Logger.info("BufferedAudioStream.destroy() called - stopping pipeline")
        streamJob?.cancel()
        rawSampleQueue.clear()
        outputBuffer = null
        // Note: Don't unregister here - the StreamRegistry calls destroy() during unregister
    }

    override fun available(): Int {
        // Return the number of bytes immediately available without blocking
        // This allows PcmAudioStream to be non-blocking

        // If we have data in the output buffer, return that size
        if (outputBuffer != null && outputPosition < outputBuffer!!.size) {
            return outputBuffer!!.size - outputPosition
        }

        // If we have enough raw samples to process, indicate data is available
        // We return a conservative estimate of output bytes
        if (rawSampleQueue.size >= minBufferSamples) {
            // Estimate: raw samples * 2 bytes per sample (16-bit PCM)
            // Conservative estimate of 1:1 pitch ratio
            return minOf(rawSampleQueue.size * 2, 8192)
        }

        // If stream is finished and we have any data left, indicate it's available
        if (finished && rawSampleQueue.isNotEmpty()) {
            return rawSampleQueue.size * 2
        }

        // No data immediately available
        return 0
    }
}
