package me.mochibit.createharmonics.audio.pcm

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Thread-safe ring buffer for PCM audio samples.
 * Allows one thread to write samples as fast as possible,
 * while another thread reads at a controlled rate.
 */
class PCMRingBuffer(
    private val capacity: Int // Maximum number of samples to store
) {
    private val buffer = ShortArray(capacity)
    private var writePos = 0
    private var readPos = 0
    private var available = 0
    private val lock = ReentrantLock()
    private val notEmpty = lock.newCondition()
    private val notFull = lock.newCondition() // Add condition for when buffer has space

    @Volatile
    var isComplete = false
        private set

    /**
     * Write samples to the buffer (called by FFmpeg decoder thread).
     * Blocks if buffer is full.
     */
    fun write(samples: ShortArray, offset: Int = 0, length: Int = samples.size - offset): Int {
        if (length == 0) return 0

        lock.withLock {
            // Wait if buffer is full - use condition variable, not sleep!
            while (available >= capacity && !isComplete) {
                try {
                    notFull.await(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                } catch (e: InterruptedException) {
                    return 0
                }
            }

            if (isComplete) return 0

            val toWrite = minOf(length, capacity - available)
            var written = 0

            for (i in 0 until toWrite) {
                buffer[writePos] = samples[offset + i]
                writePos = (writePos + 1) % capacity
                written++
            }

            available += written
            notEmpty.signal() // Wake up readers
            return written
        }
    }

    /**
     * Read samples from the buffer (called by audio processor thread).
     * Returns immediately with whatever is available, doesn't block.
     */
    fun read(samples: ShortArray, offset: Int = 0, maxLength: Int = samples.size - offset): Int {
        lock.withLock {
            val toRead = minOf(maxLength, available)
            if (toRead == 0) return 0

            for (i in 0 until toRead) {
                samples[offset + i] = buffer[readPos]
                readPos = (readPos + 1) % capacity
            }

            available -= toRead
            notFull.signal() // Wake up writers when space becomes available
            return toRead
        }
    }

    /**
     * Check how many samples are currently available in the buffer.
     */
    fun availableCount(): Int = lock.withLock { available }

    /**
     * Wait until at least minSamples are available or timeout.
     */
    fun waitForSamples(minSamples: Int, timeoutMs: Long = 100): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs

        lock.withLock {
            while (available < minSamples && !isComplete) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) return false

                try {
                    notEmpty.await(remaining, java.util.concurrent.TimeUnit.MILLISECONDS)
                } catch (e: InterruptedException) {
                    return false
                }
            }
            return available >= minSamples || isComplete
        }
    }

    /**
     * Mark the buffer as complete (no more writes will happen).
     */
    fun markComplete() {
        lock.withLock {
            isComplete = true
            notEmpty.signalAll()
        }
    }

    /**
     * Clear the buffer and reset positions.
     */
    fun clear() {
        lock.withLock {
            writePos = 0
            readPos = 0
            available = 0
            isComplete = false
        }
    }
}
