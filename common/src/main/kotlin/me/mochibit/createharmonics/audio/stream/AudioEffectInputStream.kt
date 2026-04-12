package me.mochibit.createharmonics.audio.stream

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.config.ModConfigs
import me.mochibit.createharmonics.foundation.async.modLaunch
import java.io.IOException
import java.io.InputStream
import kotlin.math.abs

class AudioEffectInputStream(
    private val audioStream: InputStream,
    private val effectChain: EffectChain,
    val sampleRate: Int,
    private val onStreamEnd: (() -> Unit)? = null,
    val onStreamHang: (() -> Unit)? = null,
    private val channels: Int = 1,
) : InputStream() {
    companion object {
        private const val RAW_BUFFER_SECONDS = 2.0
        private const val EFFECT_PROCESS_CHUNK_SIZE = 4096
    }

    val cachedMaxPitch: Double by lazy { ModConfigs.client.maxPitch.get() }

    private val bytesPerSecond get() = sampleRate * channels * 2 // 16-bit PCM
    private val rawBufferMin get() = (bytesPerSecond * 0.5).toInt()
    private val rawBufferMax get() = (bytesPerSecond * RAW_BUFFER_SECONDS * cachedMaxPitch).toInt()
    private val rawReadSize get() = (bytesPerSecond * 0.02).toInt().coerceAtLeast(4096)

    // Chunk-based deques — each entry is a ByteArray chunk
    private val rawAudioBuffer = ArrayDeque<ByteArray>()
    private var rawAudioBufferSize = 0
    private val rawBufferLock = Any()

    private val processedAudioBuffer = ArrayDeque<ByteArray>()
    private var processedAudioBufferSize = 0
    private val processedBufferLock = Any()

    // Temporary buffers for processing
    private val rawReadBuffer = ByteArray(rawReadSize)
    private val shortBuffer = ShortArray(EFFECT_PROCESS_CHUNK_SIZE / 2)
    private val outputByteBuffer = ByteArray(EFFECT_PROCESS_CHUNK_SIZE * 4)

    private var samplesProcessed = 0L

    // Tail silence for extended effects
    @Volatile private var isFlushing = false

    @Volatile private var flushSamplesRemaining = 0

    @Volatile private var flushUpdateCounter = 0

    @Volatile
    var tailFinished = CompletableDeferred<Unit>()
        private set

    val hasTail: Boolean get() {
        updateTailLength()
        return flushSamplesRemaining > 0
    }

    @Volatile var isFrozen: Boolean = false
        set(value) {
            field = value
            effectChain.setFrozen(value)
        }

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

    fun resetTailSignal() {
        if (tailFinished.isCompleted) {
            tailFinished = CompletableDeferred()
        }
    }

    fun updateTailLength() {
        val tailSeconds = effectChain.tailLengthSeconds(sampleRate)
        flushSamplesRemaining = (tailSeconds * sampleRate).toInt()
    }

    private suspend fun continuousRawBuffering() {
        try {
            while (!isClosed && !streamEnded) {
                val currentBufferSize = synchronized(rawBufferLock) { rawAudioBufferSize }
                val targetSize = calculateRawBufferTarget()

                if (currentBufferSize >= targetSize) {
                    delay(5)
                    continue
                }

                when (val bytesRead = readFromStreamSync()) {
                    -1 -> {
                        streamEnded = true
                        break
                    }

                    0 -> {
                        delay(5)
                    }

                    else -> {
                        val chunk = rawReadBuffer.copyOf(bytesRead)
                        synchronized(rawBufferLock) {
                            rawAudioBuffer.addLast(chunk)
                            rawAudioBufferSize += bytesRead
                        }

                        if (!isReady && synchronized(rawBufferLock) { rawAudioBufferSize } >= rawBufferMin) {
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
        val base = (bytesPerSecond * RAW_BUFFER_SECONDS).toInt()
        return (base * effectChain.getSpeedMultiplier())
            .toInt()
            .coerceIn(rawBufferMin, rawBufferMax)
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
            if (!isFlushing) {
                updateTailLength()
                isFlushing = true
            }

            flushUpdateCounter += EFFECT_PROCESS_CHUNK_SIZE

            if (flushUpdateCounter >= sampleRate / 4) {
                updateTailLength()
                flushUpdateCounter = 0
            }

            if (isFlushing && flushSamplesRemaining > 0) {
                val flushed = flushEffectTail()
                if (flushed) {
                    if (tailFinished.isCompleted) {
                        tailFinished
                    }
                    bytesCopied += drainProcessedBuffer(b, off + bytesCopied, len - bytesCopied)
                    return bytesCopied
                } else {
                    if (!tailFinished.isCompleted) {
                        tailFinished.complete(Unit)
                    }
                }
            }

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
        if (isFrozen) return false

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

    private fun flushEffectTail(): Boolean {
        if (flushSamplesRemaining <= 0) return false

        val chunkSamples = minOf(EFFECT_PROCESS_CHUNK_SIZE / 2, flushSamplesRemaining)
        val silence = ShortArray(chunkSamples)
        val currentTime = samplesProcessed.toDouble() / sampleRate

        val outputSamples = effectChain.process(silence, currentTime, sampleRate)
        flushSamplesRemaining -= chunkSamples
        samplesProcessed += chunkSamples

        val maxAmplitude = outputSamples.maxOfOrNull { abs(it.toInt()) } ?: 0
        if (maxAmplitude <= 62) {
            flushSamplesRemaining = 0
            return false
        }

        val outputByteCount = outputSamples.size * 2
        shortsToBytes(outputSamples, outputSamples.size, outputByteBuffer)
        val outputChunk = outputByteBuffer.copyOf(outputByteCount)

        synchronized(processedBufferLock) {
            processedAudioBuffer.addLast(outputChunk)
            processedAudioBufferSize += outputByteCount
        }

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
