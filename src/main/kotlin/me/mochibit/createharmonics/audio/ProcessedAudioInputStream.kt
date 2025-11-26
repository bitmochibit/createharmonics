package me.mochibit.createharmonics.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.mochibit.createharmonics.CommonConfig
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.processor.AudioStreamProcessor
import me.mochibit.createharmonics.audio.source.AudioSource
import me.mochibit.createharmonics.coroutine.ModCoroutineManager
import java.io.InputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

/**
 * Buffered input stream that processes audio with real-time effect chain application.
 * Raw PCM data is buffered, and effects are applied on-demand when data is read.
 */

class ProcessedAudioInputStream(
    private val audioSource: AudioSource,
    private val effectChain: EffectChain,
    private val sampleRate: Int,
    private val processor: AudioStreamProcessor
) : InputStream() {
    companion object {
        val MIN_PITCH: Float get() = CommonConfig.minPitch.get().toFloat()
        val MAX_PITCH: Float get() = CommonConfig.maxPitch.get().toFloat()

        fun ByteArray.toShortArray(): ShortArray {
            // Interpret as little-endian 16-bit PCM; ignore trailing odd byte if present
            val shorts = ShortArray(this.size / 2)
            for (i in shorts.indices) {
                val offset = i * 2
                shorts[i] = (((this[offset + 1].toInt() and 0xFF) shl 8) or
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
    }

    private val rawSampleQueue = ConcurrentLinkedQueue<Short>()

    // Maintain an atomic size counter to avoid expensive ConcurrentLinkedQueue.size calls
    private val queueSizeSamples = AtomicInteger(0)

    private val maxQueueSize: Int get() = maxOf(
        processChunkSize * 4,
        (sampleRate * MAX_PITCH).toInt()
    )

    private var outputBuffer: ByteArray? = null
    private var outputPosition = 0

    private var streamJob: Job? = null

    @Volatile
    private var finished = false

    @Volatile
    private var error: Exception? = null

    @Volatile
    private var preBuffered = false

    @Volatile
    private var paused = false

    private val preBufferLatch = java.util.concurrent.CountDownLatch(1)

    suspend fun awaitPreBuffering(timeoutSeconds: Long = 30): Boolean {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            preBufferLatch.await(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
        }
    }

    private var samplesRead = 0L

    // Minimum buffer before processing (50ms adjusted for worst-case pitch)
    // At MIN_PITCH (0.5), we need more input samples to produce the same output duration
    private val minBufferSamples: Int get() = (sampleRate * 0.05 * (1.0 / MIN_PITCH)).toInt()

    private val processChunkSize: Int get() = (sampleRate / 15 * MAX_PITCH).toInt()

    // Reusable single-byte buffer to avoid tiny allocations in read()
    private val singleByte = ByteArray(1)

    init {
        startPipeline()
    }

    private fun startPipeline() {
        streamJob = ModCoroutineManager.launch(Dispatchers.IO) {
            try {
                processor.processAudioStream(audioSource)
                    .onEach { chunk ->
                        val samples = chunk.toShortArray()

                        while ((queueSizeSamples.get() + samples.size > maxQueueSize) && !finished) {
                            delay(10)
                        }

                        if (paused && !finished) {
                            return@onEach
                        }

                        samples.forEach { rawSampleQueue.offer(it) }
                        queueSizeSamples.addAndGet(samples.size)

                        if (!preBuffered) {
                            preBuffered = true
                            preBufferLatch.countDown()
                            Logger.info("Pre-buffering completed, ${queueSizeSamples.get()} samples ready")
                        }
                    }
                    .catch { e ->
                        if (e !is CancellationException) {
                            error = e as? Exception ?: Exception(e)
                            Logger.err("Pipeline error: ${e.message}")
                        }
                    }
                    .onCompletion { cause ->
                        finished = true
                        preBufferLatch.countDown()

                        when (cause) {
                            null -> {}
                            is CancellationException -> {}
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

    // Public pause/resume API
    fun pause() {
        paused = true
        Logger.info("BufferedAudioStream paused")
    }

    fun resume() {
        paused = false
        Logger.info("BufferedAudioStream resumed")
    }

    fun isPaused(): Boolean = paused

    /**
     * Current position in source samples (input domain, pre-effects).
     */
    fun currentPositionSamples(): Long = samplesRead.toLong()

    override fun read(): Int {
        val result = read(singleByte, 0, 1)
        return if (result == -1) -1 else singleByte[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0

        // If paused, return 0 to signal no data available (keeps playback position frozen)
        if (paused) return 0

        // Check for errors
        error?.let { throw it }

        var totalRead = 0

        // Try to read data without blocking - return whatever is available
        while (totalRead < len) {
            // Stream is done
            if (finished && queueSizeSamples.get() == 0 && outputBuffer == null) {
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
            } else if (finished && queueSizeSamples.get() == 0) {
                // No data available and stream is finished
                Logger.info("Stream ended (read ${samplesRead} samples total)")
                return if (totalRead > 0) totalRead else -1
            } else if (finished && queueSizeSamples.get() > 0) {
                // Process any remaining data if stream is finishing
                processRemainingData(queueSizeSamples.get())
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
        val availableData = queueSizeSamples.get()

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
        queueSizeSamples.addAndGet(-chunkSize)

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
        queueSizeSamples.addAndGet(-sampleCount)

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
        Logger.info("BufferedAudioStream.destroy() called - stopping pipeline")
        // Mark finished and release any waiters early
        finished = true
        preBufferLatch.countDown()
        // Cancel upstream job
        streamJob?.cancel()
        rawSampleQueue.clear()
        queueSizeSamples.set(0)
        outputBuffer = null
        effectChain.reset()
    }

    override fun available(): Int {
        if (paused) return 99999999

        // Return bytes in output buffer
        outputBuffer?.let { buffer ->
            val remaining = buffer.size - outputPosition
            if (remaining > 0) return remaining
        }

        // Return estimate of bytes from raw samples
        val sampleCount = queueSizeSamples.get()
        if (sampleCount >= minBufferSamples || (finished && sampleCount > 0)) {
            return sampleCount * 2 // 2 bytes per sample
        }

        return 0
    }
}