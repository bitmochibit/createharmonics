package me.mochibit.createharmonics.audio.pcm

import me.mochibit.createharmonics.Logger
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * PCM audio processor with real-time pitch shifting support.
 * Reads from a ring buffer and applies dynamic pitch effects.
 */
class AudioEffectProcessor(
    private val sampleRate: Int
) {
    /**
     * Process PCM from a ring buffer with dynamic pitch function and real-time throttling.
     * This reads from a buffer that's being filled by FFmpeg, allowing seamless real-time
     * pitch changes (e.g., when jukebox RPM changes in-game).
     *
     * @param ringBuffer The ring buffer containing PCM samples
     * @param outputStream The output PCM stream (to FFmpeg encoder)
     * @param pitchFunction Function that returns the current pitch (can change dynamically)
     */
    fun processFromBuffer(
        ringBuffer: PCMRingBuffer,
        outputStream: OutputStream,
        pitchFunction: PitchFunction
    ) {
        Logger.info("AudioEffectProcessor starting, sample rate: $sampleRate")

        try {
            val chunkDurationMs = 25 // 25ms chunks for responsive pitch changes
            val chunkSizeSamples = (sampleRate * chunkDurationMs / 1000.0).roundToInt()

            var totalSamplesProcessed = 0
            var chunkCount = 0
            val startTime = System.nanoTime()
            var lastPitch = 0f

            // Wait for initial buffer to fill (at least 1 second of audio)
            Logger.info("Waiting for initial buffer fill...")
            ringBuffer.waitForSamples(sampleRate, timeoutMs = 5000)
            Logger.info("Initial buffer ready: ${ringBuffer.availableCount()} samples available")

            while (true) {
                val chunkStartTime = System.nanoTime()

                // Query current pitch for this chunk
                val currentTime = totalSamplesProcessed.toDouble() / sampleRate
                val currentPitch = pitchFunction.getPitchAt(currentTime)

                // Log pitch changes
                if (currentPitch != lastPitch) {
                    Logger.info("Pitch changed: $lastPitch -> $currentPitch at ${currentTime}s (buffer: ${ringBuffer.availableCount()} samples)")
                    lastPitch = currentPitch
                }

                // Calculate how many input samples we need
                val availableInBuffer = ringBuffer.availableCount()

                // If buffer is empty and complete, we're done
                if (availableInBuffer == 0 && ringBuffer.isComplete) {
                    Logger.info("Buffer exhausted and complete, finishing processing")
                    break
                }

                // Wait if buffer is too low (unless we're at the end)
                if (availableInBuffer < chunkSizeSamples / 2 && !ringBuffer.isComplete) {
                    ringBuffer.waitForSamples(chunkSizeSamples, timeoutMs = 100)
                }

                // Read what's available (up to what we need)
                val samplesToRead = minOf(chunkSizeSamples, ringBuffer.availableCount())
                if (samplesToRead == 0) {
                    if (ringBuffer.isComplete) break
                    Thread.sleep(10) // Small delay before retry
                    continue
                }

                val inputSamples = ShortArray(samplesToRead)
                val actualRead = ringBuffer.read(inputSamples, 0, samplesToRead)

                if (actualRead == 0) {
                    if (ringBuffer.isComplete) break
                    continue
                }

                // Process with current pitch
                val outputSamples = PCMProcessor.pitchShift(inputSamples, currentPitch)

                // Write output (don't flush yet - let the output stream buffer accumulate)
                val outputBytes = PCMUtils.shortsToBytes(outputSamples)
                outputStream.write(outputBytes)
                // Removed flush() here - let the buffer accumulate to 32KB

                totalSamplesProcessed += actualRead
                chunkCount++

                // No throttling needed - the audio system will handle playback timing
                // and the ring buffer already provides backpressure if we get too far ahead

                // Log every second
                if (chunkCount % 40 == 0) {
                    val elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0
                    val bufferSamples = ringBuffer.availableCount()
                    val bufferSeconds = bufferSamples.toDouble() / sampleRate
                    Logger.info("Real-time processing: ${totalSamplesProcessed / sampleRate}s processed in ${elapsedSeconds}s, pitch: $currentPitch, buffer: ${bufferSeconds.toInt()}s")
                }
            }

            val totalTime = (System.nanoTime() - startTime) / 1_000_000_000.0
            Logger.info("AudioEffectProcessor completed: $totalSamplesProcessed samples ($chunkCount chunks) in ${totalTime}s")
        } catch (e: Exception) {
            Logger.err("Error in AudioEffectProcessor: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}