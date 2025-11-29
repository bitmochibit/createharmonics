package me.mochibit.createharmonics.audio.effect.pitchShift

import me.mochibit.createharmonics.audio.effect.AudioEffect
import kotlin.math.roundToInt

class PitchShiftEffect(
    private val pitchFunction: PitchFunction,
    private val minPitch: Float = 0.5f,
    private val maxPitch: Float = 2.0f
) : AudioEffect {
    private val inputBuffer = mutableListOf<Short>()
    private var inputPosition = 0.0
    private var outputTime = 0.0

    override fun process(samples: ShortArray, timeInSeconds: Double, sampleRate: Int): ShortArray {
        inputBuffer.addAll(samples.asIterable())

        val result = mutableListOf<Short>()
        val sampleDuration = 1.0 / sampleRate

        while (inputPosition + 1 < inputBuffer.size) {
            val pitch = pitchFunction.getPitchAt(timeInSeconds + outputTime)
                .coerceIn(minPitch, maxPitch)
                .toDouble()
                .coerceIn(0.1, 10.0)

            val index = inputPosition.toInt()
            if (index < 0 || index + 1 >= inputBuffer.size) break

            val fraction = (inputPosition - index).toFloat()
            val sample1 = inputBuffer[index].toFloat()
            val sample2 = inputBuffer[index + 1].toFloat()
            val interpolated = sample1 + fraction * (sample2 - sample1)
            result.add(interpolated.roundToInt().coerceIn(-32768, 32767).toShort())

            inputPosition += pitch
            outputTime += sampleDuration
        }

        val consumedSamples = inputPosition.toInt()
        if (consumedSamples > 0) {
            repeat(minOf(consumedSamples, inputBuffer.size)) { inputBuffer.removeAt(0) }
            inputPosition -= consumedSamples
        }

        if (inputBuffer.size > sampleRate * 2) {
            val excess = inputBuffer.size - sampleRate
            repeat(excess) { inputBuffer.removeAt(0) }
            inputPosition = maxOf(0.0, inputPosition - excess)
        }

        return result.toShortArray()
    }

    override fun reset() {
        inputBuffer.clear()
        inputPosition = 0.0
        outputTime = 0.0
    }

    override fun getName(): String = "PitchShift"
}