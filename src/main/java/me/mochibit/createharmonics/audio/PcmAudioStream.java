package me.mochibit.createharmonics.audio;

import net.minecraft.client.sounds.AudioStream;
import org.jetbrains.annotations.NotNull;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Custom AudioStream implementation that works directly with PCM data
 * instead of requiring OGG encoding. This allows us to stream processed
 * audio (with pitch shifting) directly to Minecraft's audio system.
 *
 * NOTE: This is NON-BLOCKING - it returns whatever data is immediately available.
 * The underlying BufferedAudioStream handles all buffering and waiting asynchronously.
 */
public class PcmAudioStream implements AudioStream {
    private final InputStream inputStream;
    private final AudioFormat audioFormat;
    private final int maxReadSize; // Maximum bytes to return per read (for low latency)

    public PcmAudioStream(InputStream inputStream, int sampleRate) {
        this.inputStream = inputStream;

        // Create PCM audio format: 16-bit, mono, signed, little-endian
        this.audioFormat = new AudioFormat(
                sampleRate,  // sample rate
                16,          // sample size in bits
                1,           // channels (mono)
                true,        // signed
                false        // little-endian
        );

        // Limit read size to 50ms of audio for ultra-low latency pitch response
        // 50ms * sampleRate samples * 2 bytes/sample = maxReadSize
        this.maxReadSize = (int) (0.05 * sampleRate * 2);
    }

    @Override
    public @NotNull AudioFormat getFormat() {
        return audioFormat;
    }

    @Override
    public @NotNull ByteBuffer read(int size) throws IOException {
        // Cap the read size to prevent excessive buffering and reduce latency
        int actualSize = Math.min(size, maxReadSize);
        byte[] buffer = new byte[actualSize];

        // ALWAYS try to read - available() is just a hint
        // The BufferedAudioStream will block internally if needed
        int bytesRead = inputStream.read(buffer, 0, actualSize);

        if (bytesRead == -1) {
            // End of stream
            return ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());
        }

        if (bytesRead == 0) {
            // No data available right now - return empty buffer
            // Minecraft's sound engine will call us again
            return ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());
        }

        // We have data, return it
        ByteBuffer result = ByteBuffer.allocateDirect(bytesRead).order(ByteOrder.nativeOrder());
        result.put(buffer, 0, bytesRead);
        result.flip();

        return result;
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
    }
}

