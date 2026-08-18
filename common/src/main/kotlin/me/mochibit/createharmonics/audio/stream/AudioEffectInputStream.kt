package me.mochibit.createharmonics.audio.stream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.config.ModConfigs
import me.mochibit.createharmonics.foundation.async.modLaunch
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


private class ChunkedByteBuffer {
    private val lock = ReentrantLock()
    private val chunks = ArrayDeque<ByteArray>()

    @Volatile var size: Int = 0
        private set

    fun isEmpty(): Boolean = size == 0

    fun add(chunk: ByteArray) = lock.withLock {
        chunks.addLast(chunk)
        size += chunk.size
    }

    /** Copies up to [length] bytes into [dest] starting at [offset]. Returns the number of bytes copied. */
    fun drainInto(dest: ByteArray, offset: Int, length: Int): Int = lock.withLock {
        var remaining = length
        var pos = offset
        while (remaining > 0 && chunks.isNotEmpty()) {
            val head = chunks.first()
            val take = minOf(remaining, head.size)
            System.arraycopy(head, 0, dest, pos, take)
            if (take == head.size) chunks.removeFirst() else chunks[0] = head.copyOfRange(take, head.size)
            size -= take
            pos += take
            remaining -= take
        }
        length - remaining
    }

    /** Drains up to [maxBytes] into a freshly allocated array (used to move data between buffers). */
    fun drainChunk(maxBytes: Int): ByteArray = lock.withLock {
        val result = ByteArray(minOf(maxBytes, size))
        drainInto(result, 0, result.size)
        result
    }

    fun clear() = lock.withLock {
        chunks.clear()
        size = 0
    }
}

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
    private val maxLookaheadBytes get() = (bytesPerSecond * AudioLatencyConfig.MAX_DSP_LOOKAHEAD_SECONDS).toInt()

    private val rawBuffer = ChunkedByteBuffer()
    private val processedBuffer = ChunkedByteBuffer()


    private val rawReadBuffer = ByteArray(rawReadSize)
    private val shortBuffer = ShortArray(EFFECT_PROCESS_CHUNK_SIZE / 2)
    private val outputByteBuffer = ByteArray(EFFECT_PROCESS_CHUNK_SIZE * 4)

    private var samplesProcessed = 0L

    @Volatile var isClosed = false
        private set

    @Volatile private var streamEnded = false
    @Volatile private var streamEndSignaled = false
    @Volatile private var isReady = false

    private var bufferingJob: Job? = modLaunch(Dispatchers.IO) { continuousRawBuffering() }

    private suspend fun continuousRawBuffering() {
        try {
            while (!isClosed && !streamEnded) {
                if (rawBuffer.size >= calculateRawBufferTarget()) {
                    delay(5)
                    continue
                }

                when (val bytesRead = readFromStreamSafely()) {
                    -1 -> {
                        streamEnded = true
                        isReady = true
                    }
                    0 -> delay(5)
                    else -> {
                        rawBuffer.add(rawReadBuffer.copyOf(bytesRead))
                        if (!isReady && rawBuffer.size >= rawBufferMin) isReady = true
                    }
                }
            }
        } catch (e: Exception) {
            if (!isClosed) isReady = true
        }
    }

    private fun calculateRawBufferTarget(): Int {
        val base = (bytesPerSecond * RAW_BUFFER_SECONDS).toInt()
        return (base * effectChain.getSpeedMultiplier()).toInt().coerceIn(rawBufferMin, rawBufferMax)
    }

    private fun readFromStreamSafely(): Int =
        try {
            audioStream.read(rawReadBuffer, 0, rawReadBuffer.size)
        } catch (_: Exception) {
            -1
        }

    override fun read(): Int {
        val single = ByteArray(1)
        return if (read(single, 0, 1) == -1) -1 else single[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
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

    private fun readWithProcessedBuffer(b: ByteArray, off: Int, len: Int): Int {
        var copied = processedBuffer.drainInto(b, off, len)

        while (copied < len && processedBuffer.size < maxLookaheadBytes) {
            if (!ensureProcessedAudio()) break
            copied += processedBuffer.drainInto(b, off + copied, len - copied)
        }

        if (copied > 0) return copied

        if (streamEnded) {
            if (!streamEndSignaled) {
                streamEndSignaled = true
                onStreamEnd?.invoke()
            }
            return -1
        }
        return 0
    }

    /** Moves (and, if needed, processes) one chunk of audio from the raw buffer into the processed buffer. */
    private fun ensureProcessedAudio(): Boolean {
        if (effectChain.isEmpty()) {
            if (rawBuffer.isEmpty()) return false
            val chunk = rawBuffer.drainChunk(EFFECT_PROCESS_CHUNK_SIZE)
            if (chunk.isEmpty()) return false
            processedBuffer.add(chunk)
            return true
        }

        if (rawBuffer.isEmpty()) return false

        // Keep the chunk 16-bit aligned
        val bytesToProcess = minOf(EFFECT_PROCESS_CHUNK_SIZE, rawBuffer.size) and 1.inv()
        if (bytesToProcess == 0) return false

        val chunk = rawBuffer.drainChunk(bytesToProcess)
        val sampleCount = chunk.readShortsInto(shortBuffer)
        val currentTime = samplesProcessed.toDouble() / sampleRate

        val outputSamples = effectChain.process(
            if (sampleCount == shortBuffer.size) shortBuffer else shortBuffer.copyOf(sampleCount),
            currentTime,
            sampleRate,
        )

        if (outputSamples.isNotEmpty()) {
            outputSamples.writeBytesInto(outputByteBuffer)
            processedBuffer.add(outputByteBuffer.copyOf(outputSamples.size * 2))
        }

        samplesProcessed += sampleCount
        return true
    }

    override fun close() {
        if (isClosed) return
        isClosed = true

        bufferingJob?.cancel()
        bufferingJob = null

        try {
            audioStream.close()
        } catch (_: Exception) {
        }

        rawBuffer.clear()
        processedBuffer.clear()
        effectChain.reset()
    }

    override fun available(): Int {
        if (isClosed) return 0
        val speedMultiplier = effectChain.getSpeedMultiplier()
        return processedBuffer.size + (rawBuffer.size / speedMultiplier).toInt()
    }
}

/** Reinterprets this byte array as little-endian 16-bit PCM samples, writing them into [dest]. Returns sample count. */
private fun ByteArray.readShortsInto(dest: ShortArray): Int {
    val sampleCount = size / 2
    ByteBuffer.wrap(this, 0, sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(dest, 0, sampleCount)
    return sampleCount
}

/** Writes this short array as little-endian 16-bit PCM bytes into [dest]. */
private fun ShortArray.writeBytesInto(dest: ByteArray) {
    ByteBuffer.wrap(dest, 0, size * 2).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(this)
}