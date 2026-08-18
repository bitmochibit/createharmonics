package me.mochibit.createharmonics.audio.effect.reverb

import org.lwjgl.openal.AL11
import org.lwjgl.openal.ALC10
import org.lwjgl.openal.EXTEfx

object ReverbEngine {
    data class Params(
        val decayTime: Float,
        val density: Float,
        val diffusion: Float,
        val gain: Float,
        val gainHF: Float,
    )
    private var auxSlot = 0
    private var reverbEffect = 0
    private var initialized = false

    fun init() {
        if (initialized) return
        val ctx = ALC10.alcGetCurrentContext()
        val device = ALC10.alcGetContextsDevice(ctx)
        if (!ALC10.alcIsExtensionPresent(device, "ALC_EXT_EFX")) {
            return
        }

        auxSlot = EXTEfx.alGenAuxiliaryEffectSlots()
        EXTEfx.alAuxiliaryEffectSloti(auxSlot, EXTEfx.AL_EFFECTSLOT_AUXILIARY_SEND_AUTO, AL11.AL_TRUE)

        reverbEffect = EXTEfx.alGenEffects()
        EXTEfx.alEffecti(reverbEffect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB)

        initialized = true
    }

    fun attach(sourceId: Int) {
        init()
        if (!initialized) return
        AL11.alSourcef(sourceId, EXTEfx.AL_ROOM_ROLLOFF_FACTOR, 1.0f)
        AL11.alSource3i(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, auxSlot, 0, EXTEfx.AL_FILTER_NULL)
    }

    fun detach(sourceId: Int) {
        if (!initialized) return
        AL11.alSource3i(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, EXTEfx.AL_EFFECTSLOT_NULL, 0, EXTEfx.AL_FILTER_NULL)
    }
    fun setParams(params: Params) {
        if (!initialized) return
        EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_EAXREVERB_DECAY_TIME, params.decayTime)
        EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_EAXREVERB_DENSITY, params.density)
        EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_EAXREVERB_DIFFUSION, params.diffusion)
        EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_EAXREVERB_GAIN, params.gain)
        EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_EAXREVERB_GAINHF, params.gainHF)
        EXTEfx.alAuxiliaryEffectSloti(auxSlot, EXTEfx.AL_EFFECTSLOT_EFFECT, reverbEffect)
    }
}