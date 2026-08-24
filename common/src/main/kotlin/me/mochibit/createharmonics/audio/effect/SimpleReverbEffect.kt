package me.mochibit.createharmonics.audio.effect

import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplier
import org.lwjgl.openal.EXTEfx

class SimpleReverbEffect(
    private val roomSizeSupplier: FloatSupplier,
    private val dampingSupplier: FloatSupplier,
    private val wetMixSupplier: FloatSupplier,
    override val scope: AudioEffect.Scope,
) : NativeAudioEffect {
    override val alEffectType = EXTEfx.AL_EFFECT_EAXREVERB

    override fun applyParams(effectId: Int) {
        val roomSize = roomSizeSupplier.getValue().coerceIn(0f, 1f)
        val damping = dampingSupplier.getValue().coerceIn(0f, 1f)
        val wetMix = wetMixSupplier.getValue().coerceIn(0f, 1f)

        EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_DECAY_TIME, 0.3f + roomSize * 5.7f)
        EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_DENSITY, 0.3f + roomSize * 0.7f)
        EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_DIFFUSION, 1.0f)
        EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_GAIN, wetMix * 0.9f)
        EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_GAINHF, (1.0f - damping).coerceIn(0.05f, 1.0f))
    }
}
