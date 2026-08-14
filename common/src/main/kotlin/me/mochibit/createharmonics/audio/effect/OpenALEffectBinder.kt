package me.mochibit.createharmonics.audio.effect

import org.lwjgl.openal.AL10
import org.lwjgl.openal.EXTEfx

class OpenALEffectBinder(private val sourceId: Int) {
    private var lowPassFilterId: Int = -1
    private var reverbEffectId: Int = -1
    private var reverbSlotId: Int = -1

    private var lastPitch: Float? = null
    private var lastLowPass: NativeEffectSpec.LowPass? = null
    private var lastReverb: NativeEffectSpec.Reverb? = null

    fun apply(specs: Collection<NativeEffectSpec>) {
        val pitch = specs.filterIsInstance<NativeEffectSpec.Pitch>().firstOrNull()
        val lowPass = specs.filterIsInstance<NativeEffectSpec.LowPass>().firstOrNull()
        val reverb = specs.filterIsInstance<NativeEffectSpec.Reverb>().firstOrNull()

        applyPitch(pitch)
        applyLowPass(lowPass)
        applyReverb(reverb)
    }

    private fun applyPitch(pitch: NativeEffectSpec.Pitch?) {
        val value = pitch?.multiplier ?: 1f
        if (lastPitch == value) return
        AL10.alSourcef(sourceId, AL10.AL_PITCH, value.coerceIn(0.01f, 4f))
        lastPitch = value
    }

    private fun applyLowPass(spec: NativeEffectSpec.LowPass?) {
        if (spec == null) {
            if (lastLowPass != null) {
                AL10.alSourcei(sourceId, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL)
                lastLowPass = null
            }
            return
        }
        if (spec == lastLowPass) return

        if (lowPassFilterId == -1) {
            lowPassFilterId = EXTEfx.alGenFilters()
            EXTEfx.alFilteri(lowPassFilterId, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS)
        }
        EXTEfx.alFilterf(lowPassFilterId, EXTEfx.AL_LOWPASS_GAINHF, spec.cutoffFrequency)

        AL10.alSourcei(sourceId, EXTEfx.AL_DIRECT_FILTER, lowPassFilterId)
        lastLowPass = spec
    }

    private fun applyReverb(spec: NativeEffectSpec.Reverb?) {
        if (spec == null) {
            if (lastReverb != null) {
                AL10.alSource3f(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, 0f, 0f, EXTEfx.AL_FILTER_NULL.toFloat())
                lastReverb = null
            }
            return
        }
        if (spec == lastReverb) return

        if (reverbSlotId == -1) {
            reverbSlotId = EXTEfx.alGenAuxiliaryEffectSlots()
            reverbEffectId = EXTEfx.alGenEffects()
            EXTEfx.alEffecti(reverbEffectId, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_REVERB)
        }
        EXTEfx.alEffectf(reverbEffectId, EXTEfx.AL_REVERB_ROOM_ROLLOFF_FACTOR, 0f)
        EXTEfx.alEffectf(reverbEffectId, EXTEfx.AL_REVERB_DECAY_TIME, spec.roomSize.coerceIn(0.1f, 20f))
        EXTEfx.alEffectf(reverbEffectId, EXTEfx.AL_REVERB_DENSITY, spec.damping.coerceIn(0f, 1f))
        EXTEfx.alEffectf(reverbEffectId, EXTEfx.AL_REVERB_GAIN, spec.wetMix.coerceIn(0f, 1f))
        EXTEfx.alAuxiliaryEffectSloti(reverbSlotId, EXTEfx.AL_EFFECTSLOT_EFFECT, reverbEffectId)

        AL10.alSource3f(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, reverbSlotId.toFloat(), 0F,
            EXTEfx.AL_FILTER_NULL.toFloat()
        )
        lastReverb = spec
    }

    fun release() {
        if (lowPassFilterId != -1) EXTEfx.alDeleteFilters(lowPassFilterId)
        if (reverbEffectId != -1) EXTEfx.alDeleteEffects(reverbEffectId)
        if (reverbSlotId != -1) EXTEfx.alDeleteAuxiliaryEffectSlots(reverbSlotId)
    }
}