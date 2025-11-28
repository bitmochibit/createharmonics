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
    private val audioFormat = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
    private val maxReadSize = sampleRate / 15

    override fun getFormat(): AudioFormat = audioFormat

    @Throws(IOException::class)
    override fun read(size: Int): ByteBuffer {

        val actualSize = min(size, maxReadSize)
        val buffer = ByteArray(actualSize)
        val bytesRead = inputStream.read(buffer, 0, actualSize)

        if (bytesRead < 0) {
            return ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
        }

        if (bytesRead == 0) {
            val silentBuffer = ByteArray(size) { 0 }
            return ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
                .put(silentBuffer)
                .flip()
        }


        return ByteBuffer.allocateDirect(actualSize)
            .order(ByteOrder.nativeOrder())
            .put(buffer, 0, bytesRead)
            .flip()
    }

    @Throws(IOException::class)
    override fun close() {
        if (inputStream !is AudioEffectInputStream) {
            inputStream.close()
        }
    }
}