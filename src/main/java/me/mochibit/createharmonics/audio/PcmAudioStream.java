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
        int bytesRead = 0;
        int attempts = 0;
        int maxAttempts = 100; // ~1 second timeout (100 * 10ms)

        // BLOCKING: Keep trying until we get data or reach end of stream
        // Never return empty buffer unless stream is truly finished
        while (bytesRead == 0 && attempts < maxAttempts) {
            bytesRead = inputStream.read(buffer, 0, size);

            if (bytesRead == -1) {
                // True end of stream
                return ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());
            }

            if (bytesRead == 0) {
                // No data available right now, wait and retry
                // This is critical: we must NOT return empty buffer or Minecraft stops playback
                attempts++;
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for audio data", e);
                }
            }
        }

        // Check if we timed out
        if (bytesRead == 0) {
            // Timeout - check one more time if stream is done
            bytesRead = inputStream.read(buffer, 0, size);
            if (bytesRead <= 0) {
                // Truly finished or hung - return empty to signal end
                System.out.println("PcmAudioStream: Timeout waiting for data, ending stream");
                return ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());
            }
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
