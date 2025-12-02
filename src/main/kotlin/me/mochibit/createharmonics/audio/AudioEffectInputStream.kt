package me.mochibit.createharmonics.audio

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.coroutine.launchModCoroutine
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

class AudioEffectInputStream(
    private val audioStream: InputStream,
    private val effectChain: EffectChain,
    private val sampleRate: Int,
    private val onStreamEnd: (() -> Unit)? = null,
    val onStreamHang: (() -> Unit)? = null,
) : InputStream() {
    companion object {
        private const val PROCESS_BUFFER_SIZE = 8192  // 8KB - balance between latency and efficiency
        private const val TARGET_BUFFER_SIZE = 4096  // 4KB - target output buffer size
        private const val PRE_BUFFER_SIZE = 16384  // 16KB - pre-buffer to prevent initial hang
        private const val MIN_BUFFER_BEFORE_PLAY = 8192  // 8KB - minimum before playback starts

        /**
         * Convert PCM byte array to 16-bit signed short samples (little-endian).
         * Reuses the provided shorts array to avoid allocation.
         *
         * @return Number of shorts written
         */
        private fun bytesToShorts(bytes: ByteArray, length: Int, shorts: ShortArray): Int {
            val shortCount = length / 2
            for (i in 0 until shortCount) {
                val offset = i * 2
                shorts[i] = (((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                        (bytes[offset].toInt() and 0xFF)).toShort()
            }
            return shortCount
        }

        /**
         * Convert 16-bit signed short samples to PCM byte array (little-endian).
         */
        private fun shortsToBytes(shorts: ShortArray, length: Int, bytes: ByteArray) {
            for (i in 0 until length) {
                val offset = i * 2
                bytes[offset] = (shorts[i].toInt() and 0xFF).toByte()
                bytes[offset + 1] = ((shorts[i].toInt() shr 8) and 0xFF).toByte()
            }
        }
    }

    private var samplesRead = 0L
    private val singleByte = ByteArray(1)
    private val processBuffer = ByteArray(PROCESS_BUFFER_SIZE)
    private val shortBuffer = ShortArray(PROCESS_BUFFER_SIZE / 2)
    private val outputByteBuffer = ByteArray(PROCESS_BUFFER_SIZE * 2)
    private val outputBuffer = ArrayDeque<Byte>()  // O(1) add/remove operations
    private val bufferLock = Any()

    @Volatile
    private var isClosed = false

    @Volatile
    private var streamEndSignaled = false

    @Volatile
    private var streamEnded = false

    private val isPreBuffered = AtomicBoolean(false)
    private val preBufferChannel = Channel<Unit>(capacity = 1)

    // Background processing job
    private var processingJob: Job? = null

    init {
        // Start background processing that continuously fills the buffer
        processingJob = launchModCoroutine(Dispatchers.IO) {
            try {
                // First, pre-buffer audio
                preBufferAudio()

                // Then continuously process audio in background
                continuouslyProcessAudio()
            } catch (_: Exception) {
                if (!isClosed) {
                    // If pre-buffering fails, mark as complete anyway to not block reads
                    isPreBuffered.set(true)
                    preBufferChannel.trySend(Unit)
                }
            }
        }
    }

    /**
     * Suspends until pre-buffering is complete or timeout occurs.
     *
     * @param timeoutMs Timeout in milliseconds, default 5000ms
     * @return true if pre-buffering completed successfully, false on timeout
     */
    suspend fun awaitPreBuffering(timeoutMs: Long = 5000L): Boolean {
        if (isPreBuffered.get()) return true

        return withTimeoutOrNull(timeoutMs) {
            preBufferChannel.receive()
            true
        } ?: false
    }

    /**
     * Pre-buffer audio data in background to prevent initial hang.
     * Uses coroutines for non-blocking I/O operations.
     */
    private suspend fun preBufferAudio() {
        if (!waitForStreamReady()) {
            isPreBuffered.set(true)
            preBufferChannel.trySend(Unit)
            return
        }

        val targetSize = if (effectChain.isEmpty()) MIN_BUFFER_BEFORE_PLAY else PRE_BUFFER_SIZE

        // Buffer initial audio data
        var attempts = 0
        val maxAttempts = 20
        while (synchronized(bufferLock) { outputBuffer.size } < targetSize && !streamEnded && attempts < maxAttempts) {
            if (isClosed) break

            val bytesRead = readFromStream()
            when {
                bytesRead < 0 -> {
                    streamEnded = true
                    break
                }

                bytesRead == 0 -> {
                    attempts++
                    delay(50)
                    continue
                }

                else -> {
                    attempts = 0
                    processAudioChunk(bytesRead)
                }
            }
        }

        // Mark pre-buffering as complete only if we have minimum data
        val hasEnoughData = synchronized(bufferLock) { outputBuffer.size >= MIN_BUFFER_BEFORE_PLAY }
        if (hasEnoughData || streamEnded) {
            isPreBuffered.set(true)
            preBufferChannel.trySend(Unit)
        }
    }

    /**
     * Wait for the audio stream to have data available (FFmpeg connection phase).
     *
     * @return true if stream is ready, false on timeout
     */
    private suspend fun waitForStreamReady(): Boolean {
        var connectionAttempts = 0
        val maxConnectionAttempts = 20 // 20 attempts * 100ms = 2 seconds max wait

        while (connectionAttempts < maxConnectionAttempts && !isClosed) {
            try {
                if (audioStream.available() > 0) {
                    return true
                }
            } catch (_: Exception) {
                // Stream not ready yet
            }
            delay(100)
            connectionAttempts++
        }

        return false
    }

    /**
     * Read audio data from the input stream.
     *
     * @return Number of bytes read, 0 if no data available, -1 on stream end
     */
    private suspend fun readFromStream(): Int {
        return try {
            withContext(Dispatchers.IO) {
                audioStream.read(processBuffer, 0, processBuffer.size)
            }
        } catch (_: Exception) {
            -1
        }
    }

    /**
     * Process a chunk of audio data (with or without effects) and add to output buffer.
     */
    private fun processAudioChunk(bytesRead: Int) {
        if (effectChain.isEmpty()) {
            processRawAudio(bytesRead)
        } else {
            processAudioWithEffects(bytesRead)
        }
    }

    /**
     * Process raw audio without effects - directly copy to output buffer.
     */
    private fun processRawAudio(bytesRead: Int) {
        synchronized(bufferLock) {
            for (i in 0 until bytesRead) {
                outputBuffer.addLast(processBuffer[i])
            }
        }
        samplesRead += bytesRead / 2
    }

    /**
     * Process audio with effect chain applied.
     */
    private fun processAudioWithEffects(bytesRead: Int) {
        val validBytes = bytesRead and 0xFFFFFFFE.toInt()
        if (validBytes == 0) return

        val sampleCount = bytesToShorts(processBuffer, validBytes, shortBuffer)
        val currentTime = samplesRead.toDouble() / sampleRate

        val outputSamples = if (sampleCount == shortBuffer.size) {
            effectChain.process(shortBuffer, currentTime, sampleRate)
        } else {
            effectChain.process(shortBuffer.copyOf(sampleCount), currentTime, sampleRate)
        }

        if (outputSamples.isNotEmpty()) {
            val outputByteCount = outputSamples.size * 2
            shortsToBytes(outputSamples, outputSamples.size, outputByteBuffer)

            synchronized(bufferLock) {
                for (i in 0 until outputByteCount) {
                    outputBuffer.addLast(outputByteBuffer[i])
                }
            }
        }

        samplesRead += sampleCount
    }

    /**
     * Continuously process audio in background to keep buffer filled.
     * This prevents the read() method from blocking.
     */
    private suspend fun continuouslyProcessAudio() {
        while (!isClosed && !streamEnded) {
            // Only process if buffer is below target size
            val currentBufferSize = synchronized(bufferLock) { outputBuffer.size }
            if (currentBufferSize >= TARGET_BUFFER_SIZE) {
                delay(10) // Buffer is full, wait a bit
                continue
            }

            val bytesRead = readFromStream()

            when {
                bytesRead < 0 -> {
                    streamEnded = true
                    if (!streamEndSignaled) {
                        streamEndSignaled = true
                        onStreamEnd?.invoke()
                    }
                    break
                }

                bytesRead == 0 -> {
                    delay(10) // No data available, wait a bit
                    continue
                }

                else -> {
                    processAudioChunk(bytesRead)
                }
            }
        }
    }

    override fun read(): Int {
        if (isClosed) return -1

        val result = read(singleByte, 0, 1)
        return if (result == -1) -1 else singleByte[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (isClosed) return -1
        if (len == 0) return 0

        // Pre-buffering must be complete before any reads
        if (!isPreBuffered.get()) {
            return 0 // Not ready yet, don't block
        }

        try {
            // All data comes from the buffer now (filled by background job)
            val availableBytes = synchronized(bufferLock) { outputBuffer.size }

            if (availableBytes == 0) {
                // Return -1 only if stream truly ended, otherwise return 0 (no data yet)
                return if (streamEnded) -1 else 0
            }

            // Copy from output buffer efficiently using ArrayDeque's removeFirst()
            val bytesToCopy = minOf(availableBytes, len)
            synchronized(bufferLock) {
                for (i in 0 until bytesToCopy) {
                    b[off + i] = outputBuffer.removeFirst()
                }
            }

            return bytesToCopy
        } catch (_: java.io.IOException) {
            // Stream was closed while reading, signal end of stream
            isClosed = true
            return -1
        }
    }

    override fun close() {
        if (isClosed) return
        isClosed = true

        // Cancel background processing job
        processingJob?.cancel()
        processingJob = null

        preBufferChannel.close()

        try {
            audioStream.close()
        } catch (_: Exception) {
            // Ignore close errors
        }

        synchronized(bufferLock) {
            outputBuffer.clear()
        }
        effectChain.reset()
    }

    override fun available(): Int {
        if (isClosed) return 0

        return try {
            audioStream.available()
        } catch (_: java.io.IOException) {
            0
        }
    }
}