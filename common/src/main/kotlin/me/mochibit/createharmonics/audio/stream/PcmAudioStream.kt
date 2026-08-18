package me.mochibit.createharmonics.audio.stream

import net.minecraft.client.sounds.AudioStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import kotlin.math.min

//todo maybe convert this to some advanced configuration?
object AudioLatencyConfig {
    /** AL Buffer length in seconds */
    const val AL_BUFFER_SECONDS = 0.05f

    /** How many buffers of AL_BUFFER_SECONDS keep in queue, for example 10 * 0.15s = 1.5s hang resiliency */
    const val AL_QUEUED_BUFFERS = 4

    /** Max processed audio from the DSP chain. */
    const val MAX_DSP_LOOKAHEAD_SECONDS = 0.12
}

interface PausableAudioStream {
    fun isPaused(): Boolean

    fun pause()

    fun resume()
}

class PcmAudioStream(
    private val inputStream: InputStream,
    val sampleRate: Int = 44100,
) : AudioStream,
    PausableAudioStream {
    val readBufferSize = ((sampleRate * 2) * AudioLatencyConfig.AL_BUFFER_SECONDS).toInt()
        .coerceAtLeast(4096)
    private val audioFormat = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
    private var paused: Boolean = false

    override fun getFormat(): AudioFormat = audioFormat

    val readBuffer = ByteArray(readBufferSize)

    @Throws(IOException::class)
    override fun read(size: Int): ByteBuffer {
        try {
            val bytesRead = inputStream.read(readBuffer, 0, min(size, readBuffer.size))

            return when {
                bytesRead > 0 -> {
                    ByteBuffer
                        .allocateDirect(bytesRead)
                        .order(ByteOrder.nativeOrder())
                        .put(readBuffer, 0, bytesRead)
                        .flip()
                }

                bytesRead == 0 -> {
                    val silenceSize = min(size, readBuffer.size)
                    ByteBuffer
                        .allocateDirect(silenceSize)
                        .order(ByteOrder.nativeOrder())
                        .put(ByteArray(silenceSize))
                        .flip()
                }

                else -> {
                    ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
                }
            }
        } catch (e: IOException) {
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
                if (!inputStream.isClosed) {
                    inputStream.onStreamHang?.let { it() }
                }
            }

            else -> {
                inputStream.close()
            }
        }
    }

    override fun isPaused(): Boolean = paused

    override fun pause() {
        paused = true
    }

    override fun resume() {
        paused = false
    }
}
