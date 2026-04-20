package me.mochibit.createharmonics.audio.comp

import kotlinx.coroutines.Job
import me.mochibit.createharmonics.audio.effect.AudioEffect
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.effect.LowPassFilterEffect
import me.mochibit.createharmonics.audio.effect.MixerEffect
import me.mochibit.createharmonics.audio.effect.PitchShiftEffect
import me.mochibit.createharmonics.audio.effect.ScopeAnchor
import me.mochibit.createharmonics.audio.instance.SimpleTickableSoundInstance
import me.mochibit.createharmonics.audio.instance.SuppliedSoundInstance
import me.mochibit.createharmonics.audio.utils.getStreamDirectly
import me.mochibit.createharmonics.foundation.async.every
import me.mochibit.createharmonics.foundation.async.modLaunch
import me.mochibit.createharmonics.foundation.err
import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplier
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import java.util.UUID
import kotlin.concurrent.atomics.AtomicReference
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class SoundEventComposition(
    soundList: List<SoundEventDef> = listOf(),
    val soundEffectChain: EffectChain,
    @Volatile var mixerAnchor: ScopeAnchor? = null,
) {
    private val mixer = MixerEffect("SoundCompositionMixer", AudioEffect.Scope.SOUND_COMPOSITION_MIXER)

    fun anchorBefore(scope: AudioEffect.Scope) {
        mixerAnchor = ScopeAnchor.Before(scope)
    }

    fun anchorAfter(scope: AudioEffect.Scope) {
        mixerAnchor = ScopeAnchor.After(scope)
    }

    fun clearAnchor() {
        mixerAnchor = null
    }

    data class SoundEventDef(
        val event: SoundEvent,
        val source: SoundSource? = null,
        val randomSource: RandomSource? = null,
        val looping: Boolean? = null,
        val delay: Int? = null,
        val attenuation: SoundInstance.Attenuation? = null,
        val relative: Boolean? = null,
        var needStream: Boolean = false,
        var posSupplier: (() -> BlockPos)? = null,
        var pitchSupplier: FloatSupplier? = null,
        var volumeSupplier: FloatSupplier? = null,
        var radiusSupplier: FloatSupplier? = null,
        var probabilitySupplier: (() -> Float)? = null,
        var mixLevel: Float = 0.5f,
    )

    private data class ActiveEntry(
        val def: SoundEventDef,
        val instances: MutableList<String> = mutableListOf(),
        val jobs: MutableList<Job> = mutableListOf(),
    )

    private val entries: MutableList<ActiveEntry> = soundList.map { ActiveEntry(it) }.toMutableList()
    private var referenceSoundInstance: SoundInstance? = null

    val soundList: List<SoundEventDef> get() = entries.map { it.def }
    val isRunning: Boolean get() = entries.any { it.instances.isNotEmpty() || it.jobs.isNotEmpty() }

    fun makeComposition(referenceSoundInstance: SoundInstance) {
        soundEffectChain.removeEffect(mixer)
        mixer.clearSources()
        soundEffectChain.addWithAnchor(mixer, mixerAnchor)
        this.referenceSoundInstance = referenceSoundInstance
        entries.toList().forEach { startEntry(it, referenceSoundInstance) }
    }

    fun stopComposition() {
        soundEffectChain.removeEffect(mixer)
        entries.toList().forEach { stopEntry(it) }
        mixer.clearSources()
        referenceSoundInstance = null
    }

    fun add(def: SoundEventDef) {
        val entry = ActiveEntry(def)
        entries.add(entry)
        referenceSoundInstance?.let { startEntry(entry, it) }
    }

    fun remove(def: SoundEventDef) {
        val entry = entries.find { it.def == def } ?: return
        stopEntry(entry)
        entries.remove(entry)
    }

    fun removeAll(predicate: (SoundEventDef) -> Boolean) {
        val toRemove = entries.filter { predicate(it.def) }
        toRemove.forEach { stopEntry(it) }
        entries.removeAll(toRemove)
    }

    fun replace(
        old: SoundEventDef,
        new: SoundEventDef,
    ) {
        val entry = entries.find { it.def == old } ?: return
        stopEntry(entry)
        val newEntry = ActiveEntry(new)
        entries[entries.indexOf(entry)] = newEntry
        referenceSoundInstance?.let { startEntry(newEntry, it) }
    }

    private fun startEntry(
        entry: ActiveEntry,
        ref: SoundInstance,
    ) {
        val isLooping = entry.def.looping ?: false

        if (entry.def.probabilitySupplier != null && !isLooping) {
            val job =
                30.seconds.every {
                    val probability = entry.def.probabilitySupplier?.invoke() ?: 0f
                    if (Random.nextFloat() <= probability) {
                        val id =
                            UUID.randomUUID().toString() +
                                entry.def.event.location
                                    .toString()
                        entry.instances.add(id)
                        mixer.addSource(
                            id,
                            entry.def.event
                                .getStreamDirectly(false)
                                .get(),
                            entry.def.mixLevel,
                        )
                    }
                }
            entry.jobs.add(job)
        } else {
            val id =
                UUID.randomUUID().toString() +
                    entry.def.event.location
                        .toString()
            entry.instances.add(id)
            mixer.addSource(
                id,
                entry.def.event
                    .getStreamDirectly(isLooping)
                    .get(),
                entry.def.mixLevel,
                !isLooping,
            )
        }
    }

    private fun stopEntry(entry: ActiveEntry) {
        val jobsToCancel = entry.jobs.toList()
        entry.jobs.clear()
        jobsToCancel.forEach { runCatching { it.cancel() } }

        val instancesToStop = entry.instances.toList()
        entry.instances.clear()

        instancesToStop.forEach { id ->
            runCatching { mixer.removeSource(id) }
        }
    }
}
