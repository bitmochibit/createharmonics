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
 */
public class PcmAudioStream implements AudioStream {
    private final InputStream inputStream;
    private final AudioFormat audioFormat;

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
    }

    @Override
    public @NotNull AudioFormat getFormat() {
        return audioFormat;
    }

    @Override
    public @NotNull ByteBuffer read(int size) throws IOException {
        byte[] buffer = new byte[size];

        // Try to read data, but don't block waiting to fill the entire buffer
        // Return whatever is available immediately
        int bytesRead = inputStream.read(buffer, 0, size);

        if (bytesRead == -1) {
            // End of stream
            return ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());
        }

        if (bytesRead == 0) {
            // No data available right now, return empty buffer
            return ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());
        }

        // Create a direct ByteBuffer for OpenAL compatibility
        ByteBuffer result = ByteBuffer.allocateDirect(bytesRead).order(ByteOrder.nativeOrder());
        result.put(buffer, 0, bytesRead);
        result.flip(); // Set position to 0 and limit to bytesRead

        return result;
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
    }
}
