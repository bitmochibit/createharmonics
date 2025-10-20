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

    // Maximum queue size (5 seconds of audio) to ensure real-time pitch changes
    private val maxQueueSize = sampleRate * 5

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

    // Minimum samples to keep in buffer before processing (helps with interpolation)
    private val minBufferSamples = sampleRate / 10 // 100ms

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
                        StreamRegistry.unregisterStream(resourceLocation)
                        Logger.info("Pipeline finished and stream unregistered, final queue size: ${rawSampleQueue.size}")
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

                continue
            }

            // Need to process more data
            if (!processNextChunk()) {
                // No more data to process
                return if (totalRead > 0) totalRead else -1
            }
        }

        return totalRead
    }

    /**
     * Process a chunk of raw samples with on-demand pitch shifting.
     * Returns true if data was processed, false if no more data available.
     */
    private fun processNextChunk(): Boolean {
        // Wait for enough samples to be buffered (unless stream is finished)
        if (rawSampleQueue.size < minBufferSamples && !finished) {
            var attempts = 0
            while (rawSampleQueue.size < minBufferSamples && !finished && attempts < 50) {
                Thread.sleep(10)
                attempts++
            }
        }

        // If still not enough data and stream is finished, process what we have
        if (rawSampleQueue.isEmpty()) {
            return false
        }

        // Determine chunk size to process (smaller chunks for more responsive pitch changes)
        val chunkSize = minOf(sampleRate / 4, rawSampleQueue.size) // Process up to 0.25s at a time
        if (chunkSize == 0) return false

        // Extract samples from queue
        val inputSamples = ShortArray(chunkSize) { rawSampleQueue.poll() ?: 0 }

        // Get current pitch for this chunk
        val currentTime = samplesRead.toDouble() / sampleRate
        val currentPitch = pitchFunction.getPitchAt(currentTime)

        // Log pitch changes and buffer status
        if (currentPitch != lastPitch) {
            val bufferTimeSeconds = rawSampleQueue.size.toDouble() / sampleRate
            Logger.info("Applying pitch on-demand: $lastPitch -> $currentPitch at ${currentTime}s (buffer: ${rawSampleQueue.size} samples = ${String.format("%.1f", bufferTimeSeconds)}s)")
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
