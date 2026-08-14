package me.mochibit.createharmonics.audio.effect

class BitCrushEffect(
    var bitDepth: Int = 8,
    var crushRate: Int = 4,
) : PreMixEffect {
    private var holdCounter = 0
    private var heldSample: Short = 0

    override fun process(samples: ShortArray, sampleCount: Int, sampleRate: Int) {
        val mask = (0xFFFF shl (16 - bitDepth)) and 0xFFFF
        for (i in 0 until sampleCount) {
            if (holdCounter == 0) {
                heldSample = (samples[i].toInt() and mask).toShort()
                holdCounter = crushRate
            }
            samples[i] = heldSample
            holdCounter--
        }
    }

    override fun reset() {
        holdCounter = 0
        heldSample = 0
    }
}
