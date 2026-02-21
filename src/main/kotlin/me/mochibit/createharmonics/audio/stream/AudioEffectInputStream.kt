package me.mochibit.createharmonics.audio.stream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.coroutine.launchModCoroutine
import java.io.IOException
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
        private const val RAW_READ_SIZE = 4096 * 4

        // Process in larger chunks for effect continuity
        private const val EFFECT_PROCESS_CHUNK_SIZE = 4096 // 8KB chunks for processing

        // Buffer RAW audio (unprocessed)
        private const val RAW_BUFFER_TARGET = 65536
        private const val RAW_BUFFER_MAX = 131072
        private const val RAW_BUFFER_MIN = 16384
        private const val LOW_BUFFER_THRESHOLD = 4096

        // Small buffer for PROCESSED audio (keeps latency low)
        private const val PROCESSED_BUFFER_TARGET = 4096
    }

    // Buffer for RAW unprocessed audio from FFmpeg
    private val rawAudioBuffer = ArrayDeque<Byte>(RAW_BUFFER_TARGET)
    private val rawBufferLock = Any()

    // Small buffer for PROCESSED audio (reduces chunk discontinuities)
    private val processedAudioBuffer = ArrayDeque<Byte>(PROCESSED_BUFFER_TARGET)
    private val processedBufferLock = Any()

    // Temporary buffers for processing
    private val rawReadBuffer = ByteArray(RAW_READ_SIZE)
    private val processChunkBuffer = ByteArray(EFFECT_PROCESS_CHUNK_SIZE)
    private val shortBuffer = ShortArray(EFFECT_PROCESS_CHUNK_SIZE / 2)
    private val outputByteBuffer = ByteArray(EFFECT_PROCESS_CHUNK_SIZE * 4) // Extra space for time stretch

    private var samplesProcessed = 0L

    @Volatile
    private var isClosed = false

    @Volatile
    private var streamEnded = false

    @Volatile
    private var streamEndSignaled = false

    @Volatile
    private var isReady = false

    private var processingJob: Job? = null

    init {
        processingJob =
            launchModCoroutine(Dispatchers.IO) {
                continuousRawBuffering()
            }
    }

    /**
     * Continuously read RAW audio from FFmpeg and buffer it (unprocessed).
     */
    private suspend fun continuousRawBuffering() {
        if (!waitForStreamReady()) {
            isReady = true
            return
        }

        try {
            while (!isClosed && !streamEnded) {
                val currentBufferSize = synchronized(rawBufferLock) { rawAudioBuffer.size }
                val targetSize = calculateRawBufferTarget()

                if (currentBufferSize >= targetSize) {
                    delay(20)
                    continue
                }

                if (currentBufferSize < LOW_BUFFER_THRESHOLD && isReady) {
                    onStreamHang?.invoke()
                }

                when (val bytesRead = readFromStreamSync()) {
                    -1 -> {
                        streamEnded = true
                        // Don't signal end yet — rawAudioBuffer may still have data to process.
                        // The consumer (readWithProcessedBuffer) will fire onStreamEnd once
                        // both buffers are fully drained.
                        break
                    }

                    0 -> {
                        delay(20)
                    }

                    else -> {
                        synchronized(rawBufferLock) {
                            for (i in 0 until bytesRead) {
                                rawAudioBuffer.addLast(rawReadBuffer[i])
                            }
                        }

                        if (!isReady && synchronized(rawBufferLock) { rawAudioBuffer.size } >= RAW_BUFFER_MIN) {
                            isReady = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (!isClosed) {
                isReady = true
            }
        }
    }

    private fun calculateRawBufferTarget(): Int {
        val speedMultiplier = effectChain.getSpeedMultiplier()
        return when {
            speedMultiplier > 1.5f -> RAW_BUFFER_MAX
            speedMultiplier > 1.2f -> RAW_BUFFER_TARGET * 3 / 2
            speedMultiplier > 1.0f -> RAW_BUFFER_TARGET
            speedMultiplier < 0.7f -> RAW_BUFFER_TARGET / 2
            else -> RAW_BUFFER_TARGET
        }.coerceIn(RAW_BUFFER_MIN, RAW_BUFFER_MAX)
    }

    private fun waitForStreamReady(): Boolean =
        runBlocking {
            repeat(100) {
                if (isClosed) return@runBlocking false
                try {
                    if (audioStream.available() > 0) return@runBlocking true
                } catch (_: Exception) {
                }
                delay(100)
            }
            false
        }

    private fun readFromStreamSync(): Int =
        try {
            audioStream.read(rawReadBuffer, 0, rawReadBuffer.size)
        } catch (_: Exception) {
            -1
        }

    override fun read(): Int {
        if (isClosed) return -1
        val singleByte = ByteArray(1)
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
        if (!isReady) return 0

        return try {
            readWithProcessedBuffer(b, off, len)
        } catch (_: IOException) {
            isClosed = true
            -1
        }
    }

    /**
     * Read from processed buffer, processing more chunks as needed.
     * Processing happens in larger chunks to maintain effect continuity.
     */
    private fun readWithProcessedBuffer(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int {
        // First, try to serve from processed buffer
        var bytesCopied = drainProcessedBuffer(b, off, len)
        if (bytesCopied >= len) return bytesCopied

        // Need more processed audio - process a chunk
        if (!ensureProcessedAudio()) {
            // No more data to process
            if (bytesCopied > 0) return bytesCopied

            if (streamEnded) {
                // Both raw and processed buffers are empty and FFmpeg is done — true EOF
                if (!streamEndSignaled) {
                    streamEndSignaled = true
                    onStreamEnd?.invoke()
                }
                return -1
            }

            return 0
        }

        // Try again after processing
        bytesCopied += drainProcessedBuffer(b, off + bytesCopied, len - bytesCopied)
        return bytesCopied
    }

    /**
     * Process a chunk of raw audio and add to processed buffer.
     * Returns true if audio was processed, false if no raw audio available.
     */
    private fun ensureProcessedAudio(): Boolean {
        if (effectChain.isEmpty()) {
            // No effects - directly transfer raw to processed
            val bytesToTransfer =
                synchronized(rawBufferLock) {
                    minOf(rawAudioBuffer.size, EFFECT_PROCESS_CHUNK_SIZE)
                }

            if (bytesToTransfer == 0) return false

            synchronized(rawBufferLock) {
                synchronized(processedBufferLock) {
                    repeat(bytesToTransfer) {
                        processedAudioBuffer.addLast(rawAudioBuffer.removeFirst())
                    }
                }
            }
            return true
        }

        // Process a chunk with effects
        val rawBytesAvailable = synchronized(rawBufferLock) { rawAudioBuffer.size }
        if (rawBytesAvailable == 0) return false

        // Process larger chunks for continuity (align to sample boundary)
        val bytesToProcess = minOf(EFFECT_PROCESS_CHUNK_SIZE, rawBytesAvailable) and 0xFFFFFFFE.toInt()
        if (bytesToProcess == 0) return false

        // Extract raw audio chunk
        synchronized(rawBufferLock) {
            for (i in 0 until bytesToProcess) {
                processChunkBuffer[i] = rawAudioBuffer.removeFirst()
            }
        }

        // Convert to shorts and process with CURRENT effect parameters
        val sampleCount = bytesToShorts(processChunkBuffer, bytesToProcess, shortBuffer)
        val currentTime = samplesProcessed.toDouble() / sampleRate

        val outputSamples =
            effectChain.process(
                if (sampleCount == shortBuffer.size) {
                    shortBuffer
                } else {
                    shortBuffer.copyOf(sampleCount)
                },
                currentTime,
                sampleRate,
            )

        // Convert back to bytes and add to processed buffer
        if (outputSamples.isNotEmpty()) {
            val outputByteCount = outputSamples.size * 2
            shortsToBytes(outputSamples, outputSamples.size, outputByteBuffer)

            synchronized(processedBufferLock) {
                for (i in 0 until outputByteCount) {
                    processedAudioBuffer.addLast(outputByteBuffer[i])
                }
            }
        }

        samplesProcessed += sampleCount
        return true
    }

    private fun drainProcessedBuffer(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int {
        val bytesToCopy =
            synchronized(processedBufferLock) {
                minOf(processedAudioBuffer.size, len)
            }

        if (bytesToCopy == 0) return 0

        synchronized(processedBufferLock) {
            for (i in 0 until bytesToCopy) {
                b[off + i] = processedAudioBuffer.removeFirst()
            }
        }

        return bytesToCopy
    }

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

    override fun close() {
        if (isClosed) return
        isClosed = true

        processingJob?.cancel()
        processingJob = null

        try {
            audioStream.close()
        } catch (_: Exception) {
        }

        synchronized(rawBufferLock) {
            rawAudioBuffer.clear()
        }
        synchronized(processedBufferLock) {
            processedAudioBuffer.clear()
        }
        effectChain.reset()
    }

    override fun available(): Int {
        if (isClosed) return 0

        val processed = synchronized(processedBufferLock) { processedAudioBuffer.size }
        val rawBytes = synchronized(rawBufferLock) { rawAudioBuffer.size }
        val speedMultiplier = effectChain.getSpeedMultiplier()

        return processed + (rawBytes / speedMultiplier).toInt()
    }
}
