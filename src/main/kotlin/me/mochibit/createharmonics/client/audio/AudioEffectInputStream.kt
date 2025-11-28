package me.mochibit.createharmonics.client.audio

import me.mochibit.createharmonics.client.audio.effect.EffectChain
import java.io.InputStream

class AudioEffectInputStream(
    private val audioStream: InputStream,
    private val effectChain: EffectChain,
    private val sampleRate: Int,
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

    override fun read(): Int {
        val result = read(singleByte, 0, 1)
        return if (result == -1) -1 else singleByte[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0

        if (effectChain.isEmpty()) {
            val bytesRead = audioStream.read(b, off, len)
            if (bytesRead > 0) {
                samplesRead += bytesRead / 2
                return bytesRead
            }
            return 0
        }

        while (outputBuffer.size < len && outputBuffer.size < maxOutputBufferSize) {
            val bytesRead = audioStream.read(processBuffer, 0, processBuffer.size)
            if (bytesRead <= 0) break

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
            return 0
        }

        val bytesToCopy = minOf(outputBuffer.size, len)
        for (i in 0 until bytesToCopy) {
            b[off + i] = outputBuffer[i]
        }
        repeat(bytesToCopy) { outputBuffer.removeAt(0) }

        return bytesToCopy
    }

    override fun close() {
        audioStream.close()
        outputBuffer.clear()
        effectChain.reset()
    }

    override fun available(): Int {
        return audioStream.available()
    }
}