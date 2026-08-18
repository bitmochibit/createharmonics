package me.mochibit.createharmonics.audio.effect.reverb

import me.mochibit.createharmonics.audio.effect.AudioEffect
import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplier

class SimpleReverbEffect(
    private val roomSizeSupplier: FloatSupplier,
    private val dampingSupplier: FloatSupplier,
    private val wetMixSupplier: FloatSupplier,
    override val scope: AudioEffect.Scope,
) : AudioEffect {

    override fun process(samples: ShortArray, timeInSeconds: Double, sampleRate: Int): ShortArray = samples

    fun currentParams(): ReverbEngine.Params {
        val roomSize = roomSizeSupplier.getValue().coerceIn(0f, 1f)
        val damping = dampingSupplier.getValue().coerceIn(0f, 1f)
        val wetMix = wetMixSupplier.getValue().coerceIn(0f, 1f)

        return ReverbEngine.Params(
            decayTime = 0.3f + roomSize * 5.7f,
            density = 0.3f + roomSize * 0.7f,
            diffusion = 1.0f,
            gain = wetMix * 0.9f,
            gainHF = (1.0f - damping).coerceIn(0.05f, 1.0f),
        )
    }

    override fun getName(): String = "OpenALReverb(room=${roomSizeSupplier.getValue()}, damp=${dampingSupplier.getValue()}, wet=${wetMixSupplier.getValue()})"
}



