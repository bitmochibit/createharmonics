package me.mochibit.createharmonics.audio

import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.Logger.err
import me.mochibit.createharmonics.audio.PCMProcessor.pitchShift
import me.mochibit.createharmonics.audio.PCMProcessor.pitchShiftDynamic
import me.mochibit.createharmonics.audio.PCMProcessor.toShortArray
import me.mochibit.createharmonics.audio.PCMProcessor.toByteArray
import java.io.InputStream
import java.io.OutputStream

/**
 * PCM audio processor with pitch shifting support.
 * Input: Raw PCM (16-bit mono)
 * Output: Processed PCM (16-bit mono)
 *
 * Supports both static and dynamic pitch shifting.
 */
class AudioEffectProcessor(
    private val sampleRate: Int
) {
    /**
     * Process PCM stream with a constant pitch shift factor.
     */
    fun processPCMStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        pitchShiftFactor: Float
    ) {
        processPCMStream(inputStream, outputStream, PitchFunction.constant(pitchShiftFactor))
    }

    /**
     * Process PCM stream with a dynamic pitch function.
     * The pitch can vary over time based on the provided function.
     */
    fun processPCMStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        pitchFunction: PitchFunction
    ) {
        info("AudioEffectProcessor starting with dynamic pitch function, sample rate: $sampleRate")

        try {
            // Read all input data and convert to 16-bit samples
            val samples = inputStream.readAllBytes().toShortArray()
            info("Loaded ${samples.size} samples for pitch shifting")

            // Determine if we can use the simpler constant pitch shift
            val startPitch = pitchFunction.getPitchAt(0.0)
            val midPitch = pitchFunction.getPitchAt((samples.size / 2.0) / sampleRate)
            val endPitch = pitchFunction.getPitchAt(samples.size.toDouble() / sampleRate)

            val outputSamples = if (startPitch == midPitch && midPitch == endPitch) {
                // Constant pitch - use faster algorithm
                info("AudioEffectProcessor: Using constant pitch shift ($startPitch)")
                if (startPitch == 1.0f) {
                    info("AudioEffectProcessor: Pass-through mode (no effects)")
                    outputStream.write(inputStream.readAllBytes())
                    outputStream.flush()
                    return
                }
                samples.pitchShift(startPitch)
            } else {
                // Dynamic pitch - use time-varying algorithm
                info("AudioEffectProcessor: Using dynamic pitch shift algorithm")
                samples.pitchShiftDynamic(pitchFunction, sampleRate)
            }

            info("Generated ${outputSamples.size} output samples")

            // Write output samples
            outputSamples.writeToStream(outputStream)
            outputStream.flush()

            info("AudioEffectProcessor completed: ${outputSamples.size * 2} bytes written")
        } catch (e: Exception) {
            err("Error in AudioEffectProcessor: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    private fun ShortArray.writeToStream(outputStream: OutputStream, chunkSize: Int = 8192) {
        val bytes = this.toByteArray()
        var offset = 0

        while (offset < bytes.size) {
            val toWrite = minOf(chunkSize, bytes.size - offset)
            outputStream.write(bytes, offset, toWrite)
            offset += toWrite

            if (offset % 200000 == 0 && offset > 0) {
                info("Written $offset bytes...")
                outputStream.flush()
            }
        }
    }
}
