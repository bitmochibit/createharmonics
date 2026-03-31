package me.mochibit.createharmonics.audio.stream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.config.ModConfigs
import me.mochibit.createharmonics.foundation.async.modLaunch
import java.io.IOException
import java.io.InputStream

class AudioEffectInputStream(
    private val audioStream: InputStream,
    private val effectChain: EffectChain,
    private val sampleRate: Int,
    private val onStreamEnd: (() -> Unit)? = null,
    val onStreamHang: (() -> Unit)? = null,
) : InputStream() {
    companion object {
        private const val RAW_READ_SIZE = 4096 * 4
        private const val EFFECT_PROCESS_CHUNK_SIZE = 4096

        private val RAW_BUFFER_MAX =
            RAW_READ_SIZE *
                ModConfigs.client.maxPitch
                    .get()
                    .toInt()
        private const val RAW_BUFFER_MIN = RAW_READ_SIZE
        private val RAW_BUFFER_TARGET = RAW_BUFFER_MAX / 2

        private const val PROCESSED_BUFFER_TARGET = 4096
    }

    // Chunk-based deques — each entry is a ByteArray chunk
    private val rawAudioBuffer = ArrayDeque<ByteArray>()
    private var rawAudioBufferSize = 0
    private val rawBufferLock = Any()

    private val processedAudioBuffer = ArrayDeque<ByteArray>()
    private var processedAudioBufferSize = 0
    private val processedBufferLock = Any()

    // Temporary buffers for processing
    private val rawReadBuffer = ByteArray(RAW_READ_SIZE)
    private val processChunkBuffer = ByteArray(EFFECT_PROCESS_CHUNK_SIZE)
    private val shortBuffer = ShortArray(EFFECT_PROCESS_CHUNK_SIZE / 2)
    private val outputByteBuffer = ByteArray(EFFECT_PROCESS_CHUNK_SIZE * 4)

    private var samplesProcessed = 0L

    @Volatile var isClosed = false
        private set

    @Volatile private var streamEnded = false

    @Volatile private var streamEndSignaled = false

    @Volatile private var isReady = false

    private var processingJob: Job? = null

    init {
        processingJob =
            modLaunch(Dispatchers.IO) {
                continuousRawBuffering()
            }
    }

    private suspend fun continuousRawBuffering() {
        try {
            while (!isClosed && !streamEnded) {
                val currentBufferSize = synchronized(rawBufferLock) { rawAudioBufferSize }
                val targetSize = calculateRawBufferTarget()

                if (currentBufferSize >= targetSize) {
                    delay(20)
                    continue
                }

                when (val bytesRead = readFromStreamSync()) {
                    -1 -> {
                        streamEnded = true
                        break
                    }

                    0 -> {
                        delay(20)
                    }

                    else -> {
                        val chunk = rawReadBuffer.copyOf(bytesRead)
                        synchronized(rawBufferLock) {
                            rawAudioBuffer.addLast(chunk)
                            rawAudioBufferSize += bytesRead
                        }

                        if (!isReady && synchronized(rawBufferLock) { rawAudioBufferSize } >= RAW_BUFFER_MIN) {
                            isReady = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (!isClosed) isReady = true
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

    private fun readWithProcessedBuffer(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int {
        var bytesCopied = drainProcessedBuffer(b, off, len)
        if (bytesCopied >= len) return bytesCopied

        if (!ensureProcessedAudio()) {
            if (bytesCopied > 0) return bytesCopied

            if (streamEnded) {
                if (!streamEndSignaled) {
                    streamEndSignaled = true
                    onStreamEnd?.invoke()
                }
                return -1
            }

            return 0
        }

        bytesCopied += drainProcessedBuffer(b, off + bytesCopied, len - bytesCopied)
        return bytesCopied
    }

    private fun ensureProcessedAudio(): Boolean {
        if (effectChain.isEmpty()) {
            val chunk =
                synchronized(rawBufferLock) {
                    if (rawAudioBuffer.isEmpty()) return false
                    drainRawChunk(EFFECT_PROCESS_CHUNK_SIZE)
                }
            if (chunk.isEmpty()) return false

            synchronized(processedBufferLock) {
                processedAudioBuffer.addLast(chunk)
                processedAudioBufferSize += chunk.size
            }
            return true
        }

        val rawBytesAvailable = synchronized(rawBufferLock) { rawAudioBufferSize }
        if (rawBytesAvailable == 0) return false

        val bytesToProcess = minOf(EFFECT_PROCESS_CHUNK_SIZE, rawBytesAvailable) and 0xFFFFFFFE.toInt()
        if (bytesToProcess == 0) return false

        val chunk = synchronized(rawBufferLock) { drainRawChunk(bytesToProcess) }

        val sampleCount = bytesToShorts(chunk, chunk.size, shortBuffer)
        val currentTime = samplesProcessed.toDouble() / sampleRate

        val outputSamples =
            effectChain.process(
                if (sampleCount == shortBuffer.size) shortBuffer else shortBuffer.copyOf(sampleCount),
                currentTime,
                sampleRate,
            )

        if (outputSamples.isNotEmpty()) {
            val outputByteCount = outputSamples.size * 2
            shortsToBytes(outputSamples, outputSamples.size, outputByteBuffer)
            val outputChunk = outputByteBuffer.copyOf(outputByteCount)

            synchronized(processedBufferLock) {
                processedAudioBuffer.addLast(outputChunk)
                processedAudioBufferSize += outputByteCount
            }
        }

        samplesProcessed += sampleCount
        return true
    }

    /**
     * Drains up to [maxBytes] bytes from the raw buffer into a single ByteArray.
     * Must be called while holding [rawBufferLock].
     */
    private fun drainRawChunk(maxBytes: Int): ByteArray {
        val result = ByteArray(minOf(maxBytes, rawAudioBufferSize))
        var remaining = result.size
        var offset = 0

        while (remaining > 0 && rawAudioBuffer.isNotEmpty()) {
            val head = rawAudioBuffer.first()
            val take = minOf(remaining, head.size)
            System.arraycopy(head, 0, result, offset, take)

            if (take == head.size) {
                rawAudioBuffer.removeFirst()
            } else {
                // Partial consume — replace head with remainder
                val leftover = head.copyOfRange(take, head.size)
                rawAudioBuffer[0] = leftover
            }

            rawAudioBufferSize -= take
            offset += take
            remaining -= take
        }

        return result
    }

    /**
     * Drains up to [len] bytes from the processed buffer directly into [b].
     * Must be called while holding [processedBufferLock] only around size reads;
     * actual drain is done under lock.
     */
    private fun drainProcessedBuffer(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int {
        var remaining = len
        var offset = off

        synchronized(processedBufferLock) {
            while (remaining > 0 && processedAudioBuffer.isNotEmpty()) {
                val head = processedAudioBuffer.first()
                val take = minOf(remaining, head.size)
                System.arraycopy(head, 0, b, offset, take)

                if (take == head.size) {
                    processedAudioBuffer.removeFirst()
                } else {
                    processedAudioBuffer[0] = head.copyOfRange(take, head.size)
                }

                processedAudioBufferSize -= take
                offset += take
                remaining -= take
            }
        }

        return len - remaining
    }

    private fun bytesToShorts(
        bytes: ByteArray,
        length: Int,
        shorts: ShortArray,
    ): Int {
        val shortCount = length / 2
        for (i in 0 until shortCount) {
            val o = i * 2
            shorts[i] = (((bytes[o + 1].toInt() and 0xFF) shl 8) or (bytes[o].toInt() and 0xFF)).toShort()
        }
        return shortCount
    }

    private fun shortsToBytes(
        shorts: ShortArray,
        length: Int,
        bytes: ByteArray,
    ) {
        for (i in 0 until length) {
            val o = i * 2
            bytes[o] = (shorts[i].toInt() and 0xFF).toByte()
            bytes[o + 1] = ((shorts[i].toInt() shr 8) and 0xFF).toByte()
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
            rawAudioBufferSize = 0
        }
        synchronized(processedBufferLock) {
            processedAudioBuffer.clear()
            processedAudioBufferSize = 0
        }
        effectChain.reset()
    }

    override fun available(): Int {
        if (isClosed) return 0

        val processed = synchronized(processedBufferLock) { processedAudioBufferSize }
        val rawBytes = synchronized(rawBufferLock) { rawAudioBufferSize }
        val speedMultiplier = effectChain.getSpeedMultiplier()

        return processed + (rawBytes / speedMultiplier).toInt()
    }
}
