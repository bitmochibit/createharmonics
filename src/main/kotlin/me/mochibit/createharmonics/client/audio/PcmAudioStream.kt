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
    private val maxReadSize = sampleRate / 15
    private val audioFormat = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)

    override fun getFormat(): AudioFormat = audioFormat

    @Throws(IOException::class)
    override fun read(size: Int): ByteBuffer {
        try {
            val actualSize = min(size, maxReadSize)
            val buffer = ByteArray(actualSize)
            var totalBytesRead = 0

            // Keep reading until we fill the buffer or reach end of stream
            while (totalBytesRead < actualSize) {
                val bytesRead = inputStream.read(buffer, totalBytesRead, actualSize - totalBytesRead)

                if (bytesRead < 0) {
                    // True end of stream - only return empty if we haven't read anything yet
                    if (totalBytesRead == 0) {
                        return ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
                    }
                    break
                }

                if (bytesRead == 0) {
                    // No data available right now, pad with silence to maintain sample rate
                    break
                }

                totalBytesRead += bytesRead
            }

            // Always return the full requested size, padding with silence if needed
            // This ensures consistent sample rate and prevents Minecraft from thinking it's EOS
            return if (totalBytesRead < actualSize) {
                // Pad remaining bytes with silence (zeros)
                ByteBuffer.allocateDirect(actualSize)
                    .order(ByteOrder.nativeOrder())
                    .put(buffer, 0, totalBytesRead)
                    .put(ByteArray(actualSize - totalBytesRead) { 0 })
                    .flip()
            } else {
                ByteBuffer.allocateDirect(actualSize)
                    .order(ByteOrder.nativeOrder())
                    .put(buffer, 0, totalBytesRead)
                    .flip()
            }
        } catch (e: IOException) {
            // Stream was closed, return empty buffer to signal end of stream
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