package me.mochibit.createharmonics.audio.effect

import org.lwjgl.openal.AL10
import org.lwjgl.openal.AL11
import org.lwjgl.openal.ALC10
import org.lwjgl.openal.EXTEfx

object NativeEffectSlotManager {
    private class SlotHandle(val auxSlot: Int) {
        val effects = HashMap<Int, Int>()
    }

    private val slotsBySource = HashMap<Int, SlotHandle>()
    private var efxSupported = false
    private var checked = false

    private fun ensureChecked() {
        if (checked) return
        checked = true
        val ctx = ALC10.alcGetCurrentContext()
        val device = ALC10.alcGetContextsDevice(ctx)
        efxSupported = ALC10.alcIsExtensionPresent(device, "ALC_EXT_EFX")
    }

    private fun clearAlError() {
        AL10.alGetError()
    }

    private fun isValidSource(sourceId: Int): Boolean {
        val valid = AL10.alIsSource(sourceId)
        clearAlError()
        return valid
    }

    fun attach(sourceId: Int, effect: NativeAudioEffect) {
        ensureChecked()
        if (!efxSupported) return

        if (!isValidSource(sourceId)) {
            slotsBySource.remove(sourceId)?.let { destroySlot(it) }
            return
        }

        val handle = slotsBySource.getOrPut(sourceId) {
            val slot = EXTEfx.alGenAuxiliaryEffectSlots()
            EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_AUXILIARY_SEND_AUTO, AL11.AL_TRUE)
            SlotHandle(slot)
        }

        AL11.alSourcef(sourceId, EXTEfx.AL_ROOM_ROLLOFF_FACTOR, 1.0f)
        AL11.alSource3i(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, handle.auxSlot, 0, EXTEfx.AL_FILTER_NULL)

        val effectId = handle.effects.getOrPut(effect.alEffectType) {
            val id = EXTEfx.alGenEffects()
            EXTEfx.alEffecti(id, EXTEfx.AL_EFFECT_TYPE, effect.alEffectType)
            id
        }

        effect.applyParams(effectId)
        EXTEfx.alAuxiliaryEffectSloti(handle.auxSlot, EXTEfx.AL_EFFECTSLOT_EFFECT, effectId)
        clearAlError()
    }

    fun syncParams(sourceId: Int, effect: NativeAudioEffect) {
        val handle = slotsBySource[sourceId] ?: return

        if (!isValidSource(sourceId)) {
            slotsBySource.remove(sourceId)
            destroySlot(handle)
            return
        }

        val effectId = handle.effects[effect.alEffectType] ?: return
        effect.applyParams(effectId)
        EXTEfx.alAuxiliaryEffectSloti(handle.auxSlot, EXTEfx.AL_EFFECTSLOT_EFFECT, effectId)
        clearAlError()
    }

    fun detach(sourceId: Int) {
        val handle = slotsBySource.remove(sourceId) ?: return

        if (isValidSource(sourceId)) {
            AL11.alSource3i(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, EXTEfx.AL_EFFECTSLOT_NULL, 0, EXTEfx.AL_FILTER_NULL)
            clearAlError()
        }

        destroySlot(handle)
    }

    private fun destroySlot(handle: SlotHandle) {
        handle.effects.values.forEach { EXTEfx.alDeleteEffects(it) }
        EXTEfx.alDeleteAuxiliaryEffectSlots(handle.auxSlot)
        clearAlError()
    }
}