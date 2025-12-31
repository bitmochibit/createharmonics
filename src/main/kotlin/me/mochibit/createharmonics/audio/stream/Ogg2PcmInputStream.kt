package me.mochibit.createharmonics.audio.stream

import net.minecraft.client.sounds.AudioStream
import java.io.InputStream
import java.nio.ByteBuffer

class Ogg2PcmInputStream(
    val ogg: AudioStream,
    private val chunkSize: Int = 16384,
) : InputStream() {
    private var currentBuffer: ByteBuffer = ByteBuffer.allocate(0)
    private var endOfStream = false

    override fun read(): Int {
        val buf = ByteArray(1)
        val r = read(buf, 0, 1)
        return if (r == -1) -1 else buf[0].toInt() and 0xFF
    }

    override fun read(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int {
        if (b.isEmpty() || len == 0) return 0
        if (endOfStream && !currentBuffer.hasRemaining()) return -1

        var totalRead = 0
        var currentOffset = off
        var remaining = len

        while (remaining > 0) {
            if (!currentBuffer.hasRemaining()) {
                if (!fillBuffer()) {
                    return if (totalRead == 0) -1 else totalRead
                }
            }

            val toRead = minOf(remaining, currentBuffer.remaining())
            currentBuffer.get(b, currentOffset, toRead)
            totalRead += toRead
            currentOffset += toRead
            remaining -= toRead
        }

        return totalRead
    }

    private fun fillBuffer(): Boolean {
        if (endOfStream) return false

        val byteBuffer =
            ogg.read(chunkSize) ?: run {
                endOfStream = true
                return false
            }

        if (!byteBuffer.hasRemaining()) {
            endOfStream = true
            return false
        }

        currentBuffer = byteBuffer
        return true
    }

    override fun available(): Int = currentBuffer.remaining()

    override fun close() {
        endOfStream = true
        currentBuffer.clear()
        ogg.close()
    }
}
