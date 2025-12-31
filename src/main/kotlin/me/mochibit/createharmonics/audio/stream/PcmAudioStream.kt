package me.mochibit.createharmonics.audio.stream

import net.minecraft.client.sounds.AudioStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import kotlin.math.min

class PcmAudioStream(
    private val inputStream: InputStream,
) : AudioStream {
    val sampleRate: Int = 48000

    val readBufferSize = 8192
    private val audioFormat = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)

    override fun getFormat(): AudioFormat = audioFormat

    val readBuffer = ByteArray(readBufferSize)

    @Throws(IOException::class)
    override fun read(size: Int): ByteBuffer {
        try {
            val bytesRead = inputStream.read(readBuffer, 0, min(size, readBuffer.size))

            // If no data
            if (bytesRead <= 0) {
                return ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
            }

            return ByteBuffer
                .allocateDirect(bytesRead)
                .order(ByteOrder.nativeOrder())
                .put(readBuffer, 0, bytesRead)
                .flip()
        } catch (e: IOException) {
            // On error, return silence instead of empty buffer to prevent premature stream end
            val silenceSize = min(size, readBuffer.size)
            return ByteBuffer
                .allocateDirect(silenceSize)
                .order(ByteOrder.nativeOrder())
                .put(ByteArray(silenceSize))
                .flip()
        }
    }

    @Throws(IOException::class)
    override fun close() {
        // apparently if you make the game lag for long enough every streamed source is simply skipped, what the actual fuck???????
        // MOJANG FIX THIS ABSOLUTE, COSMICAL, COLOSSAL, HUMONGOUS DOGSHIT

        when (inputStream) {
            is AudioEffectInputStream -> {
                // Since the buffer is smaller (and more susceptible to hanging) the stream closure will be handled by the stream directly
                inputStream.onStreamHang?.let { it() }
            }

            else -> {
                inputStream.close()
            }
        }
    }
}
