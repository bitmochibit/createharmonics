package me.mochibit.createharmonics.audio.stream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.coroutine.launchModCoroutine
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

// TODO: Make audio end after complete effects from buffers are exhausted, and not only if source has ended, so effects like delay can continue even after the audio end
class AudioEffectInputStream(
    private val audioStream: InputStream,
    private val effectChain: EffectChain,
    private val sampleRate: Int,
    private val onStreamEnd: (() -> Unit)? = null,
    val onStreamHang: (() -> Unit)? = null,
) : InputStream() {
    companion object {
        private const val PROCESS_BUFFER_SIZE = 4096 // 4KB - smaller chunks for lower latency
        private const val PRE_BUFFER_SIZE = 8192 // 8KB - brief pre-buffer to prevent initial hang
        private const val MIN_BUFFER_BEFORE_PLAY = 4096 // 4KB - minimum before playback starts
        private const val MAX_PRE_BUFFER_ATTEMPTS = 10
        private const val STREAM_READY_CHECK_DELAY_MS = 100L
        private const val MAX_STREAM_READY_ATTEMPTS = 20 // 20 * 100ms = 2 seconds max
    }

    private var samplesRead = 0L
    private val singleByte = ByteArray(1)
    private val processBuffer = ByteArray(PROCESS_BUFFER_SIZE)
    private val shortBuffer = ShortArray(PROCESS_BUFFER_SIZE / 2)
    private val outputByteBuffer = ByteArray(PROCESS_BUFFER_SIZE * 2)
    private val outputBuffer = ArrayDeque<Byte>() // O(1) add/remove operations
    private val bufferLock = Any()

    private val bufferSize: Int
        get() = synchronized(bufferLock) { outputBuffer.size }

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

    /**
     * Convert PCM byte array to 16-bit signed short samples (little-endian).
     * @return Number of shorts written
     */
    private fun bytesToShorts(
        bytes: ByteArray,
        length: Int,
        shorts: ShortArray,
    ): Int {
        val shortCount = length / 2
        for (i in 0 until shortCount) {
            val offset = i * 2
            shorts[i] =
                (
                    ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                        (bytes[offset].toInt() and 0xFF)
                ).toShort()
        }
        return shortCount
    }

    /**
     * Convert 16-bit signed short samples to PCM byte array (little-endian).
     */
    private fun shortsToBytes(
        shorts: ShortArray,
        length: Int,
        bytes: ByteArray,
    ) {
        for (i in 0 until length) {
            val offset = i * 2
            bytes[offset] = (shorts[i].toInt() and 0xFF).toByte()
            bytes[offset + 1] = ((shorts[i].toInt() shr 8) and 0xFF).toByte()
        }
    }

    init {
        processingJob =
            launchModCoroutine(Dispatchers.IO) {
                try {
                    preBufferAudio()
                } catch (_: Exception) {
                    if (!isClosed) signalPreBufferComplete()
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
     * Pre-buffer a small amount of audio data to prevent initial hang.
     */
    private suspend fun preBufferAudio() {
        if (!waitForStreamReady()) {
            signalPreBufferComplete()
            return
        }

        val targetSize = if (effectChain.isEmpty()) MIN_BUFFER_BEFORE_PLAY else PRE_BUFFER_SIZE
        var attempts = 0

        while (bufferSize < targetSize && !streamEnded && !isClosed && attempts < MAX_PRE_BUFFER_ATTEMPTS) {
            when (val bytesRead = readFromStreamSync()) {
                -1 -> {
                    streamEnded = true
                    break
                }

                0 -> {
                    attempts++
                    delay(50)
                }

                else -> {
                    attempts = 0
                    processAudioChunk(bytesRead)
                }
            }
        }

        signalPreBufferComplete()
    }

    private fun signalPreBufferComplete() {
        isPreBuffered.set(true)
        preBufferChannel.trySend(Unit)
    }

    /**
     * Wait for the audio stream to have data available (FFmpeg connection phase).
     */
    private suspend fun waitForStreamReady(): Boolean {
        repeat(MAX_STREAM_READY_ATTEMPTS) {
            if (isClosed) return false
            try {
                if (audioStream.available() > 0) return true
            } catch (_: Exception) {
                // Stream not ready yet
            }
            delay(STREAM_READY_CHECK_DELAY_MS)
        }

        return false
    }

    /**
     * Read audio data from the input stream (synchronous).
     * Used both during pre-buffering and when read() is called.
     *
     * @return Number of bytes read, 0 if no data available, -1 on stream end
     */
    private fun readFromStreamSync(): Int =
        try {
            audioStream.read(processBuffer, 0, processBuffer.size)
        } catch (_: Exception) {
            -1
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

        val outputSamples =
            if (sampleCount == shortBuffer.size) {
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

    override fun read(): Int {
        if (isClosed) return -1

        val result = read(singleByte, 0, 1)
        return if (result == -1) -1 else singleByte[0].toInt() and 0xFF
    }

    override fun read(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int {
        if (isClosed) return -1
        if (len == 0) return 0
        if (!isPreBuffered.get()) return 0 // Not ready yet, don't block

        return try {
            readInternal(b, off, len)
        } catch (_: IOException) {
            isClosed = true
            -1
        }
    }

    private fun readInternal(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int {
        var totalBytesCopied = drainBuffer(b, off, len)

        if (totalBytesCopied >= len) return totalBytesCopied
        if (streamEnded) return if (totalBytesCopied > 0) totalBytesCopied else -1

        // Need more data - read from stream and process on-demand
        when (val bytesRead = readFromStreamSync()) {
            -1 -> {
                streamEnded = true
                if (!streamEndSignaled) {
                    streamEndSignaled = true
                    onStreamEnd?.invoke()
                }
                return if (totalBytesCopied > 0) totalBytesCopied else -1
            }

            0 -> {
                return if (totalBytesCopied > 0) totalBytesCopied else 0
            }

            else -> {
                processAudioChunk(bytesRead)
                totalBytesCopied += drainBuffer(b, off + totalBytesCopied, len - totalBytesCopied)
                return totalBytesCopied
            }
        }
    }

    private fun drainBuffer(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int {
        val bytesToCopy = minOf(bufferSize, len)
        if (bytesToCopy == 0) return 0

        synchronized(bufferLock) {
            for (i in 0 until bytesToCopy) {
                b[off + i] = outputBuffer.removeFirst()
            }
        }
        return bytesToCopy
    }

    override fun close() {
        if (isClosed) return
        isClosed = true

        // Cancel background processing job
        processingJob?.cancel()
        processingJob = null

        try {
            audioStream.close()
        } catch (_: Exception) {
            // Ignore close errors
        }

        synchronized(bufferLock) {
            outputBuffer.clear()
        }
        effectChain.reset()

        preBufferChannel.close()
    }

    override fun available(): Int {
        if (isClosed) return 0

        return try {
            audioStream.available()
        } catch (_: IOException) {
            0
        }
    }
}
