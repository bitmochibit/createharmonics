package me.mochibit.createharmonics.audio.effect

import mixin.SoundEngineAccessor
import mixin.SoundManagerAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.Sound
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.AudioStream
import net.minecraft.core.BlockPos
import net.minecraft.core.Position
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.Vec3
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import kotlin.math.roundToInt

/**
 * Mixer effect that blends audio from a secondary source with the primary audio.
 * Useful for creating layered effects or background music mixing.
 *
 * Can accept either:
 * - A function that provides secondary audio samples at a given time
 * - A Minecraft AudioStream interface (for mixing Minecraft sound effects, and adding effects on it (requires gathering the direct audio stream))
 *
 * @param secondarySource Function that provides secondary audio samples at a given time
 * @param mixLevel Mix level from 0.0 (only primary) to 1.0 (only secondary), 0.5 = equal mix
 */
class MixerEffect private constructor(
    private val secondarySource: ((timeInSeconds: Double, sampleCount: Int, sampleRate: Int) -> ShortArray)?,
    private val audioStreamSource: AudioStream?,
    private val mixLevel: Float = 0.5f,
) : AudioEffect {
    /**
     * Create a MixerEffect with a function-based secondary source
     */
    constructor(
        secondarySource: (timeInSeconds: Double, sampleCount: Int, sampleRate: Int) -> ShortArray,
        mixLevel: Float = 0.5f,
    ) : this(secondarySource, null, mixLevel)

    /**
     * Create a MixerEffect with an AudioStream-based secondary source
     */
    constructor(
        audioStreamSource: AudioStream,
        mixLevel: Float = 0.5f,
    ) : this(null, audioStreamSource, mixLevel)

    private val audioStreamBuffer = ByteArray(8192)
    private val audioStreamShortBuffer = ShortArray(4096)

    override fun process(
        samples: ShortArray,
        timeInSeconds: Double,
        sampleRate: Int,
    ): ShortArray {
        // Get secondary audio samples based on source type
        val secondarySamples =
            try {
                when {
                    secondarySource != null -> {
                        secondarySource.invoke(timeInSeconds, samples.size, sampleRate)
                    }

                    audioStreamSource != null -> {
                        readFromAudioStream(samples.size)
                    }

                    else -> {
                        // No source available, return original samples
                        return samples
                    }
                }
            } catch (e: Exception) {
                // If secondary source fails, return original samples
                return samples
            }

        // Ensure both arrays have the same size
        val minSize = minOf(samples.size, secondarySamples.size)
        val output = ShortArray(samples.size)

        val mixLevelClamped = mixLevel.coerceIn(0.0f, 1.0f)
        val primaryLevel = 1.0f - mixLevelClamped
        val secondaryLevel = mixLevelClamped

        for (i in 0 until minSize) {
            val primary = samples[i].toFloat()
            val secondary = secondarySamples[i].toFloat()

            // Mix the two signals
            val mixed = primary * primaryLevel + secondary * secondaryLevel
            output[i] = mixed.roundToInt().coerceIn(-32768, 32767).toShort()
        }

        // If primary is longer than secondary, fill with remaining primary samples
        for (i in minSize until samples.size) {
            val primary = samples[i].toFloat()
            val mixed = primary * primaryLevel
            output[i] = mixed.roundToInt().coerceIn(-32768, 32767).toShort()
        }

        return output
    }

    /**
     * Read samples from AudioStream and convert to ShortArray
     */
    private fun readFromAudioStream(sampleCount: Int): ShortArray {
        if (audioStreamSource == null) return ShortArray(0)

        val bytesToRead = sampleCount * 2 // 2 bytes per sample (16-bit)
        val buffer: ByteBuffer = audioStreamSource.read(bytesToRead)

        val bytesAvailable = buffer.remaining()
        val samplesAvailable = bytesAvailable / 2

        if (samplesAvailable == 0) {
            return ShortArray(0)
        }

        // Read bytes from buffer
        buffer.get(audioStreamBuffer, 0, bytesAvailable)

        // Convert bytes to shorts (16-bit PCM, little-endian)
        val resultSamples = minOf(samplesAvailable, sampleCount)
        for (i in 0 until resultSamples) {
            val offset = i * 2
            audioStreamShortBuffer[i] =
                (
                    ((audioStreamBuffer[offset + 1].toInt() and 0xFF) shl 8) or
                        (audioStreamBuffer[offset].toInt() and 0xFF)
                ).toShort()
        }

        return audioStreamShortBuffer.copyOf(resultSamples)
    }

    override fun reset() {
        // Reset any internal state if needed
    }

    override fun getName(): String {
        val sourceType =
            when {
                audioStreamSource != null -> "AudioStream"
                secondarySource != null -> "Function"
                else -> "None"
            }
        return "Mixer(source=$sourceType, level=${String.format("%.2f", mixLevel)})"
    }

    companion object {
        /**
         * Create a simple silence source (useful for testing or as a default)
         */
        fun silenceSource(): (Double, Int, Int) -> ShortArray =
            { _, count, _ ->
                ShortArray(count) { 0 }
            }

        /**
         * Create a simple tone generator source (useful for testing)
         */
        fun toneSource(
            frequency: Float,
            amplitude: Float = 0.3f,
        ): (Double, Int, Int) -> ShortArray =
            { time, count, sampleRate ->
                ShortArray(count) { i ->
                    val t = time + (i.toDouble() / sampleRate)
                    val sample = (amplitude * 32767 * Math.sin(2.0 * Math.PI * frequency * t))
                    sample.roundToInt().coerceIn(-32768, 32767).toShort()
                }
            }

        /**
         * Create a noise source (useful for creating static/interference effects)
         */
        fun noiseSource(amplitude: Float = 0.1f): (Double, Int, Int) -> ShortArray =
            { _, count, _ ->
                ShortArray(count) {
                    ((Math.random() - 0.5) * 2.0 * amplitude * 32767)
                        .roundToInt()
                        .coerceIn(-32768, 32767)
                        .toShort()
                }
            }
    }
}
