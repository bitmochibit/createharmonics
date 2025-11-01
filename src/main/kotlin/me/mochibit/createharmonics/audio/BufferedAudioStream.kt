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
import me.mochibit.createharmonics.audio.processor.AudioStreamProcessor
import me.mochibit.createharmonics.audio.source.AudioSource
import me.mochibit.createharmonics.coroutine.ModCoroutineManager
import java.io.InputStream
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Buffered input stream that processes audio with real-time effect chain application.
 * Raw PCM data is buffered, and effects are applied on-demand when data is read.
 */


fun ByteArray.toShortArray(): ShortArray {
    val shorts = ShortArray(this.size / 2)
    for (i in shorts.indices) {
        val offset = i * 2
        shorts[i] = ((this[offset + 1].toInt() and 0xFF) shl 8 or
                (this[offset].toInt() and 0xFF)).toShort()
    }
    return shorts
}

fun ShortArray.toByteArray(): ByteArray {
    val bytes = ByteArray(this.size * 2)
    for (i in this.indices) {
        val offset = i * 2
        bytes[offset] = (this[i].toInt() and 0xFF).toByte()
        bytes[offset + 1] = ((this[i].toInt() shr 8) and 0xFF).toByte()
    }
    return bytes
}

class BufferedAudioStream(
    private val audioSource: AudioSource,
    private val effectChain: EffectChain,
    private val sampleRate: Int,
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

    // Maximum queue size - adjusted for worst-case pitch to prevent buffer starvation
    // At MAX_PITCH (2.0), we consume input 2x faster, so we need more buffer headroom
    // Ensure it's at least large enough to hold several chunks
    private val maxQueueSize: Int get() = maxOf(
        processChunkSize * 4,  // At least 4 chunks worth
        (sampleRate * TARGET_PLAYBACK_BUFFER_SECONDS * MAX_PITCH).toInt()
    )

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

    // Suspending function to wait for pre-buffering asynchronously
    suspend fun awaitPreBuffering(timeoutSeconds: Long = 30): Boolean {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            preBufferLatch.await(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
        }
    }

    // Track playback time for effects
    private var samplesRead = 0L

    // Minimum buffer before processing (50ms adjusted for worst-case pitch)
    // At MIN_PITCH (0.5), we need more input samples to produce the same output duration
    private val minBufferSamples: Int get() = (sampleRate * 0.05 * (1.0 / MIN_PITCH)).toInt()

    // Process chunk size: adjust for pitch to maintain consistent output duration
    // With time-stretching pitch shift, higher pitch = faster consumption of input
    // Target ~0.25 seconds of OUTPUT audio duration
    // At pitch 2.0: need 0.25 * 48000 * 2.0 = 24000 input samples -> ~12000 output samples (0.25s)
    // At pitch 1.0: need 0.25 * 48000 * 1.0 = 12000 input samples -> ~12000 output samples (0.25s)
    // At pitch 0.5: need 0.25 * 48000 * 0.5 = 6000 input samples -> ~12000 output samples (0.25s)
    // Use MAX_PITCH as worst case to avoid draining buffer too fast
    private val processChunkSize: Int get() = (sampleRate /15 * MAX_PITCH).toInt()

    init {
        startPipeline()
    }

    private fun startPipeline() {
        streamJob = ModCoroutineManager.launch(Dispatchers.IO) {
            try {
                Logger.info("Starting audio pipeline for: ${audioSource.getIdentifier()}")

                processor.processAudioStream(audioSource)
                    .onEach { chunk ->
                        val samples = chunk.toShortArray()

                        // Apply backpressure if queue is full
                        while (rawSampleQueue.size + samples.size > maxQueueSize && !finished) {
                            kotlinx.coroutines.delay(10)
                        }

                        samples.forEach { rawSampleQueue.offer(it) }

                        // Mark as pre-buffered after first chunk
                        if (!preBuffered) {
                            preBuffered = true
                            preBufferLatch.countDown()
                            Logger.info("Pre-buffering completed, ${rawSampleQueue.size} samples ready")
                        }
                    }
                    .catch { e ->
                        if (e !is kotlinx.coroutines.CancellationException) {
                            error = e as? Exception ?: Exception(e)
                            Logger.err("Pipeline error: ${e.message}")
                        }
                    }
                    .onCompletion { cause ->
                        finished = true
                        preBufferLatch.countDown()

                        when (cause) {
                            null -> Logger.info("Pipeline finished normally")
                            is kotlinx.coroutines.CancellationException -> Logger.info("Pipeline cancelled")
                            else -> Logger.err("Pipeline error: ${cause.message}")
                        }
                    }
                    .collect()
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    error = e
                    Logger.err("Pipeline error: ${e.message}")
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


        // Check for errors
        error?.let { throw it }

        var totalRead = 0

        // Try to read data without blocking - return whatever is available
        while (totalRead < len) {
            // Stream is done
            if (finished && rawSampleQueue.isEmpty() && outputBuffer == null) {
                return if (totalRead > 0) totalRead else -1
            }

            // Read from output buffer if available
            val buffer = outputBuffer
            if (buffer != null && outputPosition < buffer.size) {
                val available = buffer.size - outputPosition
                val toRead = minOf(len - totalRead, available)

                System.arraycopy(buffer, outputPosition, b, off + totalRead, toRead)
                outputPosition += toRead
                totalRead += toRead

                if (outputPosition >= buffer.size) {
                    outputBuffer = null
                    outputPosition = 0
                }
            } else if (processNextChunk()) {
                // Successfully processed a chunk, continue reading
            } else if (finished && rawSampleQueue.isEmpty()) {
                // No data available and stream is finished
                Logger.info("Stream ended (read ${samplesRead} samples total)")
                return if (totalRead > 0) totalRead else -1
            } else if (finished && rawSampleQueue.isNotEmpty()) {
                // Process any remaining data if stream is finishing
                processRemainingData(rawSampleQueue.size)
            } else {
                // No data available right now
                // Return what we have so far (non-blocking behavior)
                // If we haven't read anything yet, return 0 to signal no data available
                return totalRead
            }
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
        outputBuffer = outputSamples.toByteArray()
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

        outputBuffer = outputSamples.toByteArray()
        outputPosition = 0

        samplesRead += inputSamples.size
        return true
    }

    override fun close() {
        Logger.info("BufferedAudioStream.close() called")
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
        // Return bytes in output buffer
        outputBuffer?.let { buffer ->
            val remaining = buffer.size - outputPosition
            if (remaining > 0) return remaining
        }

        // Return estimate of bytes from raw samples
        val sampleCount = rawSampleQueue.size
        if (sampleCount >= minBufferSamples || (finished && sampleCount > 0)) {
            return sampleCount * 2 // 2 bytes per sample
        }

        return 0
    }
}