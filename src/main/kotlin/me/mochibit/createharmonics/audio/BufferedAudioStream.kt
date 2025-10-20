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
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.pcm.PCMUtils
import me.mochibit.createharmonics.audio.processor.AudioStreamProcessor
import me.mochibit.createharmonics.audio.source.AudioSource
import me.mochibit.createharmonics.coroutine.ModCoroutineManager
import net.minecraft.resources.ResourceLocation
import java.io.InputStream
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Buffered input stream that processes audio with real-time effect chain application.
 * Raw PCM data is buffered, and effects are applied on-demand when data is read.
 */
class BufferedAudioStream(
    private val audioSource: AudioSource,
    private val effectChain: EffectChain,
    private val sampleRate: Int,
    private val resourceLocation: ResourceLocation,
    private val processor: AudioStreamProcessor
) : InputStream() {
    companion object {
        // Effect constraints - configurable limits (for pitch shifting)
        val MIN_PITCH: Float get() = Config.MIN_PITCH.get().toFloat()
        val MAX_PITCH: Float get() = Config.MAX_PITCH.get().toFloat()

        // Buffer size calculation
        val TARGET_PLAYBACK_BUFFER_SECONDS: Double get() = Config.PLAYBACK_BUFFER_SECONDS.get()
    }

    // Queue of raw PCM samples (unprocessed)
    private val rawSampleQueue = ConcurrentLinkedQueue<Short>()

    // Maximum queue size - use configured buffer time directly for minimal latency
    // Note: Removed pitch multiplier to minimize buffering and improve pitch response time
    private val maxQueueSize: Int get() = (sampleRate * TARGET_PLAYBACK_BUFFER_SECONDS).toInt()

    // Output buffer for processed data (pre-processed chunks)
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

    // Track playback time for effects
    private var samplesRead = 0L

    // Minimum samples to keep in buffer before processing - reduced to prevent starvation
    private val minBufferSamples: Int get() = (sampleRate * 0.01 * (1.0 / MIN_PITCH)).toInt() // 10ms minimum for ultra-low latency

    // Process smaller chunks for immediate pitch response
    private val processChunkSize = sampleRate / 20 // 50ms chunks for ultra-low latency

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
                processor.processAudioStream(audioSource)
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
        val maxConsecutiveFailures = 20
        var totalWaitCycles = 0
        val maxTotalWaitCycles = 200 // 2000ms timeout (increased from 500ms to prevent premature termination)

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

                consecutiveFailures = 0
                totalWaitCycles = 0
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
                    return if (totalRead > 0) totalRead else -1
                }

                consecutiveFailures++
                totalWaitCycles++

                if (consecutiveFailures >= maxConsecutiveFailures) {
                    val queueSize = rawSampleQueue.size

                    if (queueSize > 0) {
                        if (processRemainingData(queueSize)) {
                            consecutiveFailures = 0
                            continue
                        }
                    }

                    // CRITICAL: Only return partial data if we have SOMETHING to return
                    // Never return 0 bytes unless stream is truly finished
                    if (totalRead > 0) {
                        return totalRead
                    } else if (finished) {
                        return -1
                    } else {
                        // Still streaming but no data yet - keep waiting
                        if (totalWaitCycles >= maxTotalWaitCycles) {
                            Logger.warn("Stream stuck after ${totalWaitCycles * 10}ms with no data - ending")
                            finished = true
                            return -1 // Return -1 instead of 0 to signal end
                        }
                        consecutiveFailures = 0
                    }
                }

                Thread.sleep(5) // Reduced from 10ms for faster response
                continue
            }

            consecutiveFailures = 0
        }

        return totalRead
    }

    /**
     * Process a chunk of raw samples with the effect chain.
     * Returns true if data was processed, false if no more data available.
     */
    private fun processNextChunk(): Boolean {
        val availableData = rawSampleQueue.size

        // If we have no data at all, bail out
        if (availableData == 0) {
            return false
        }

        // If below minimum buffer and stream not finished, wait for more data
        if (availableData < minBufferSamples && !finished) {
            return false
        }

        // Process whatever we have available (up to processChunkSize)
        val chunkSize = minOf(processChunkSize, availableData)

        if (chunkSize == 0) return false

        // Extract samples from queue
        val inputSamples = ShortArray(chunkSize) {
            rawSampleQueue.poll() ?: 0
        }

        // Calculate current time for effect processing
        val currentTime = samplesRead.toDouble() / sampleRate

        // Apply effect chain
        val outputSamples = if (effectChain.isEmpty()) {
            inputSamples // No effects, pass through
        } else {
            effectChain.process(inputSamples, currentTime, sampleRate)
        }

        // Convert to bytes and store in output buffer
        outputBuffer = PCMUtils.shortsToBytes(outputSamples)
        outputPosition = 0

        // Update read counter based on INPUT samples consumed
        samplesRead += inputSamples.size

        // Log effect application periodically
        if (samplesRead % (sampleRate * 5) < chunkSize) { // Every ~5 seconds
            val bufferTimeSeconds = rawSampleQueue.size.toDouble() / sampleRate
            Logger.info("Applied effects at ${String.format("%.2f", currentTime)}s (buffer: ${String.format("%.1f", bufferTimeSeconds)}s) - ${effectChain.getName()}")
        }

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

        val outputSamples = if (effectChain.isEmpty()) {
            inputSamples
        } else {
            effectChain.process(inputSamples, currentTime, sampleRate)
        }

        Logger.info("Processing final ${sampleCount} samples with effect chain")

        outputBuffer = PCMUtils.shortsToBytes(outputSamples)
        outputPosition = 0

        samplesRead += inputSamples.size

        return true
    }

    private fun waitForPreBuffer() {
        if (!preBuffered) {
            try {
                Logger.info("Waiting for pre-buffering...")
                // Block until we have initial data (with timeout)
                if (!preBufferLatch.await(30, java.util.concurrent.TimeUnit.SECONDS)) {
                    Logger.err("Pre-buffering timeout! No audio data received after 30 seconds")
                    error?.let { throw it }
                    throw java.io.IOException("Pre-buffering timeout - no audio data received")
                }
                Logger.info("Pre-buffering completed, starting playback")
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw java.io.IOException("Pre-buffering interrupted", e)
            }
        }
    }

    override fun close() {
        Logger.info("BufferedAudioStream.close() called - clearing local buffers only")
        // Intentionally don't cancel streamJob or unregister
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
        effectChain.reset()
    }

    override fun available(): Int {
        // Return the number of bytes immediately available without blocking

        // If we have data in the output buffer, return that size
        if (outputBuffer != null && outputPosition < outputBuffer!!.size) {
            return outputBuffer!!.size - outputPosition
        }

        // If we have enough raw samples to process, indicate data is available
        if (rawSampleQueue.size >= minBufferSamples) {
            // Conservative estimate of output bytes
            return minBufferSamples * 2 // 2 bytes per sample
        }

        // If stream is finished and we have any data left, it's available
        if (finished && rawSampleQueue.isNotEmpty()) {
            return rawSampleQueue.size * 2
        }

        return 0
    }
}
