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
    val sampleRate: Int = 44100,
) : AudioStream {
    val readBufferSize = 8192
    private val audioFormat = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)

    override fun getFormat(): AudioFormat = audioFormat

    val readBuffer = ByteArray(readBufferSize)

    @Throws(IOException::class)
    override fun read(size: Int): ByteBuffer {
        try {
            val bytesRead = inputStream.read(readBuffer, 0, min(size, readBuffer.size))

            return when {
                bytesRead > 0 -> {
                    // Normal data
                    ByteBuffer
                        .allocateDirect(bytesRead)
                        .order(ByteOrder.nativeOrder())
                        .put(readBuffer, 0, bytesRead)
                        .flip()
                }

                bytesRead == 0 -> {
                    // Buffer temporarily empty (stream loading or starved) — return silence
                    // so Minecraft does not interpret this as end-of-stream.
                    val silenceSize = min(size, readBuffer.size)
                    ByteBuffer
                        .allocateDirect(silenceSize)
                        .order(ByteOrder.nativeOrder())
                        .put(ByteArray(silenceSize))
                        .flip()
                }

                else -> {
                    // bytesRead == -1: true end-of-stream
                    ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
                }
            }
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
