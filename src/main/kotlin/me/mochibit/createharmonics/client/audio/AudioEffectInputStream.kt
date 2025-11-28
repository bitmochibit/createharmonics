package me.mochibit.createharmonics.client.audio

import me.mochibit.createharmonics.client.audio.effect.EffectChain
import java.io.InputStream

class AudioEffectInputStream(
    private val audioStream: InputStream,
    private val effectChain: EffectChain,
    private val sampleRate: Int,
    private val onStreamEnd: (() -> Unit)? = null
) : InputStream() {
    companion object {
        fun ByteArray.toShortArray(): ShortArray {
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

    private var samplesRead = 0L
    private val singleByte = ByteArray(1)
    private val processBuffer = ByteArray(16384)
    private val outputBuffer = mutableListOf<Byte>()
    private val maxOutputBufferSize = 65536

    @Volatile
    private var isClosed = false

    @Volatile
    private var streamEndSignaled = false

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
                // Return 0 to let PcmAudioStream handle padding with silence
                return 0
            }

            // Try to fill the output buffer with processed audio
            while (outputBuffer.size < len && outputBuffer.size < maxOutputBufferSize) {
                if (isClosed) return -1

                val bytesRead = audioStream.read(processBuffer, 0, processBuffer.size)
                if (bytesRead < 0) {
                    // True end of stream
                    break
                }

                if (bytesRead == 0) {
                    // No data available, stop trying to read more
                    break
                }

                val inputSamples = processBuffer.copyOf(bytesRead).toShortArray()
                val currentTime = samplesRead.toDouble() / sampleRate
                val outputSamples = effectChain.process(inputSamples, currentTime, sampleRate)

                if (outputSamples.isEmpty()) {
                    samplesRead += inputSamples.size
                    continue
                }

                outputBuffer.addAll(outputSamples.toByteArray().asIterable())
                samplesRead += inputSamples.size
            }

            if (outputBuffer.isEmpty()) {
                // Signal stream end if we haven't already
                if (!streamEndSignaled) {
                    streamEndSignaled = true
                    onStreamEnd?.invoke()
                }
                // Return 0 to signal no data available (will be padded with silence by PcmAudioStream)
                return 0
            }

            val bytesToCopy = minOf(outputBuffer.size, len)
            for (i in 0 until bytesToCopy) {
                b[off + i] = outputBuffer[i]
            }
            repeat(bytesToCopy) { outputBuffer.removeAt(0) }

            return bytesToCopy
        } catch (e: java.io.IOException) {
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
        } catch (e: Exception) {
            // Ignore close errors
        }

        outputBuffer.clear()
        effectChain.reset()
    }

    override fun available(): Int {
        if (isClosed) return 0

        return try {
            audioStream.available()
        } catch (e: java.io.IOException) {
            0
        }
    }
}