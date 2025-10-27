package me.mochibit.createharmonics.audio

import net.minecraft.client.sounds.AudioStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import kotlin.math.min

/**
 * Minecraft audio stream implementation for working correctly with PCM audio data.
 */
class PcmAudioStream(private val inputStream: InputStream, sampleRate: Int) : AudioStream {
    // Create PCM audio format: 16-bit, mono, signed, little-endian
    private val audioFormat: AudioFormat = AudioFormat(
        sampleRate.toFloat(),  // sample rate
        16,  // sample size in bits
        1,  // channels (mono)
        true,  // signed
        false // little-endian
    )

    // Limit read size to 50ms of audio for ultra-low latency pitch response
    // 50ms * sampleRate samples * 2 bytes/sample = maxReadSize
    private val maxReadSize: Int = (0.05 * sampleRate * 2).toInt() // Maximum bytes to return per read (for low latency)

    override fun getFormat(): AudioFormat {
        return audioFormat
    }

    @Throws(IOException::class)
    override fun read(size: Int): ByteBuffer {
        // Cap the read size to prevent excessive buffering and reduce latency
        val actualSize = min(size, maxReadSize)
        val buffer = ByteArray(actualSize)

        // ALWAYS try to read - available() is just a hint
        // The BufferedAudioStream will block internally if needed
        val bytesRead = inputStream.read(buffer, 0, actualSize)

        if (bytesRead == -1) {
            // End of stream
            return ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
        }

        if (bytesRead == 0) {
            // No data available right now - return empty buffer
            // Minecraft's sound engine will call us again
            return ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
        }

        // We have data, return it
        val result = ByteBuffer.allocateDirect(bytesRead).order(ByteOrder.nativeOrder())
        result.put(buffer, 0, bytesRead)
        result.flip()

        return result
    }

    @Throws(IOException::class)
    override fun close() {
        inputStream.close()
    }
}