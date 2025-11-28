package me.mochibit.createharmonics.client.audio

import net.minecraft.client.sounds.AudioStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import kotlin.math.min

class PcmAudioStream(private val inputStream: InputStream) : AudioStream {
    val sampleRate: Int = 48000
    private val maxReadSize = sampleRate / 10
    private val audioFormat = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)

    override fun getFormat(): AudioFormat = audioFormat

    val readBuffer = ByteArray(maxReadSize)

    @Throws(IOException::class)
    override fun read(size: Int): ByteBuffer {
        try {
            val actualSize = min(size, maxReadSize)
            val bytesRead = inputStream.read(readBuffer, 0, actualSize)

            if (bytesRead <= 0) {
                // End of stream or no data
                return ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
            }

            // Return only what we actually read - no padding needed
            return ByteBuffer.allocateDirect(bytesRead)
                .order(ByteOrder.nativeOrder())
                .put(readBuffer, 0, bytesRead)
                .flip()
        } catch (e: IOException) {
            return ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
        }
    }

    @Throws(IOException::class)
    override fun close() {
        if (inputStream !is AudioEffectInputStream) {
            inputStream.close()
        }
    }
}