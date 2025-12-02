package me.mochibit.createharmonics.audio

import me.mochibit.createharmonics.audio.effect.EffectChain
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class AudioEffectInputStream(
    private val audioStream: InputStream,
    private val effectChain: EffectChain,
    private val sampleRate: Int,
    private val onStreamEnd: (() -> Unit)? = null,
    val onStreamHang: (() -> Unit)? = null,
) : InputStream() {
    companion object {
        // Convert bytes to shorts in-place (reuse buffer to avoid allocation)
        fun bytesToShorts(bytes: ByteArray, length: Int, shorts: ShortArray): Int {
            val shortCount = length / 2
            for (i in 0 until shortCount) {
                val offset = i * 2
                shorts[i] = (((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                        (bytes[offset].toInt() and 0xFF)).toShort()
            }
            return shortCount
        }

        fun shortsToBytes(shorts: ShortArray, length: Int, bytes: ByteArray) {
            for (i in 0 until length) {
                val offset = i * 2
                bytes[offset] = (shorts[i].toInt() and 0xFF).toByte()
                bytes[offset + 1] = ((shorts[i].toInt() shr 8) and 0xFF).toByte()
            }
        }
    }

    private var samplesRead = 0L
    private val singleByte = ByteArray(1)
    private val processBuffer = ByteArray(8192)  // Smaller buffer for lower latency
    private val shortBuffer = ShortArray(processBuffer.size / 2)  // Reusable buffer
    private val outputByteBuffer = ByteArray(processBuffer.size * 2)  // Reusable output buffer
    private val outputBuffer = ArrayDeque<Byte>()  // Use ArrayDeque for O(1) removal
    private val targetBufferSize = 4096  // Target buffer size, not maximum
    private val preBufferSize = 8192  // Pre-buffer more data to avoid initial hang

    @Volatile
    private var isClosed = false

    @Volatile
    private var streamEndSignaled = false

    @Volatile
    private var streamEnded = false

    private val isPreBuffered = AtomicBoolean(false)
    private val preBufferLock = Object()

    init {
        // Start pre-buffering in background thread to avoid blocking game thread
        thread(start = true, isDaemon = true, name = "AudioEffectInputStream-PreBuffer") {
            try {
                preBufferAudio()
            } catch (_: Exception) {
                // If pre-buffering fails, mark as complete anyway to not block reads
                synchronized(preBufferLock) {
                    isPreBuffered.set(true)
                    preBufferLock.notifyAll()
                }
            }
        }
    }

    /**
     * Pre-buffer audio data in background to prevent initial hang
     */
    private fun preBufferAudio() {
        if (effectChain.isEmpty()) {
            // No effects, no need to pre-buffer
            synchronized(preBufferLock) {
                isPreBuffered.set(true)
                preBufferLock.notifyAll()
            }
            return
        }

        // Wait for stream to have data available (FFmpeg connection phase)
        var connectionAttempts = 0
        val maxConnectionAttempts = 20 // 20 attempts * 100ms = 2 seconds max wait
        while (connectionAttempts < maxConnectionAttempts && !isClosed) {
            try {
                val available = audioStream.available()
                if (available > 0) {
                    break // Stream is ready
                }
            } catch (_: Exception) {
                // Stream not ready yet
            }
            Thread.sleep(100)
            connectionAttempts++
        }

        // If still no data available after waiting, mark as pre-buffered anyway
        if (connectionAttempts >= maxConnectionAttempts) {
            synchronized(preBufferLock) {
                isPreBuffered.set(true)
                preBufferLock.notifyAll()
            }
            return
        }

        // Now buffer initial audio data
        var attempts = 0
        val maxAttempts = 10
        while (outputBuffer.size < preBufferSize && !streamEnded && attempts < maxAttempts) {
            if (isClosed) break

            val bytesRead = try {
                audioStream.read(processBuffer, 0, processBuffer.size)
            } catch (_: Exception) {
                -1
            }

            if (bytesRead < 0) {
                streamEnded = true
                break
            }

            if (bytesRead == 0) {
                // No data yet, wait a bit and retry
                attempts++
                Thread.sleep(50)
                continue
            }

            attempts = 0 // Reset attempts on successful read

            val validBytes = bytesRead and 0xFFFFFFFE.toInt()
            if (validBytes == 0) continue

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

                synchronized(outputBuffer) {
                    for (i in 0 until outputByteCount) {
                        outputBuffer.addLast(outputByteBuffer[i])
                    }
                }
            }

            samplesRead += sampleCount
        }

        // Mark pre-buffering as complete
        synchronized(preBufferLock) {
            isPreBuffered.set(true)
            preBufferLock.notifyAll()
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

        try {
            if (effectChain.isEmpty()) {
                val bytesRead = audioStream.read(b, off, len)
                if (bytesRead > 0) {
                    samplesRead += bytesRead / 2
                    return bytesRead
                }
                return bytesRead  // Return -1 on EOF, 0 if no data available
            }

            // Wait for pre-buffering to complete (with timeout to prevent infinite hang)
            if (!isPreBuffered.get()) {
                synchronized(preBufferLock) {
                    val timeout = 5000L // 5 second timeout
                    val startTime = System.currentTimeMillis()
                    while (!isPreBuffered.get() && !isClosed) {
                        val elapsed = System.currentTimeMillis() - startTime
                        if (elapsed >= timeout) {
                            // Timeout, proceed anyway
                            break
                        }
                        preBufferLock.wait(timeout - elapsed)
                    }
                }
            }

            // Only process more audio if we need it (don't overfill buffer)
            while (outputBuffer.size < targetBufferSize && !streamEnded) {
                if (isClosed) return -1

                val bytesRead = audioStream.read(processBuffer, 0, processBuffer.size)
                if (bytesRead < 0) {
                    // True end of stream
                    streamEnded = true
                    if (!streamEndSignaled) {
                        streamEndSignaled = true
                        onStreamEnd?.invoke()
                    }
                    break
                }

                if (bytesRead == 0) {
                    // No data available, stop trying to read more
                    break
                }

                // Ensure we have even number of bytes (for 16-bit samples)
                val validBytes = bytesRead and 0xFFFFFFFE.toInt()
                if (validBytes == 0) continue

                // Convert bytes to shorts in-place (no copy!)
                val sampleCount = bytesToShorts(processBuffer, validBytes, shortBuffer)
                val currentTime = samplesRead.toDouble() / sampleRate

                // Process the samples - pass the buffer directly with the valid sample count
                // EffectChain will handle copying if needed
                val outputSamples = if (sampleCount == shortBuffer.size) {
                    effectChain.process(shortBuffer, currentTime, sampleRate)
                } else {
                    // Only copy when we have partial buffer (rare at end of stream)
                    effectChain.process(shortBuffer.copyOf(sampleCount), currentTime, sampleRate)
                }

                if (outputSamples.isEmpty()) {
                    samplesRead += sampleCount
                    continue
                }

                // Convert back to bytes and add to buffer
                val outputByteCount = outputSamples.size * 2
                shortsToBytes(outputSamples, outputSamples.size, outputByteBuffer)

                // Add to output buffer efficiently with synchronization
                synchronized(outputBuffer) {
                    for (i in 0 until outputByteCount) {
                        outputBuffer.addLast(outputByteBuffer[i])
                    }
                }

                samplesRead += sampleCount
            }

            val availableBytes = synchronized(outputBuffer) { outputBuffer.size }

            if (availableBytes == 0) {
                // Return -1 only if stream truly ended, otherwise return 0 (no data yet)
                return if (streamEnded) -1 else 0
            }

            // Copy from output buffer efficiently using ArrayDeque's removeFirst()
            val bytesToCopy = minOf(availableBytes, len)
            synchronized(outputBuffer) {
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

        try {
            audioStream.close()
        } catch (_: Exception) {
            // Ignore close errors
        }

        outputBuffer.clear()
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