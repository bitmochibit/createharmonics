package me.mochibit.createharmonics.audio.comp

import kotlinx.coroutines.Job
import me.mochibit.createharmonics.audio.instance.SimpleTickableSoundInstance
import me.mochibit.createharmonics.audio.instance.SuppliedSoundInstance
import me.mochibit.createharmonics.foundation.async.every
import me.mochibit.createharmonics.foundation.async.modLaunch
import me.mochibit.createharmonics.foundation.err
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class SoundEventComposition(
    soundList: List<SoundEventDef> = listOf(),
) {
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
        var pitchSupplier: (() -> Float)? = null,
        var volumeSupplier: (() -> Float)? = null,
        var radiusSupplier: (() -> Int)? = null,
        var probabilitySupplier: (() -> Float)? = null,
    )

    private data class ActiveEntry(
        val def: SoundEventDef,
        val instances: MutableList<SoundInstance> = mutableListOf(),
        val jobs: MutableList<Job> = mutableListOf(),
    )

    private val entries: MutableList<ActiveEntry> = soundList.map { ActiveEntry(it) }.toMutableList()
    private var referenceSoundInstance: SoundInstance? = null

    val soundList: List<SoundEventDef> get() = entries.map { it.def }
    val isRunning: Boolean get() = entries.any { it.instances.isNotEmpty() || it.jobs.isNotEmpty() }

    fun makeComposition(referenceSoundInstance: SoundInstance) {
        this.referenceSoundInstance = referenceSoundInstance
        entries.forEach { startEntry(it, referenceSoundInstance) }
    }

    fun stopComposition() {
        entries.forEach { stopEntry(it) }
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
                        val instance = createSoundInstance(entry.def, ref, false)
                        entry.instances.add(instance)
                        Minecraft.getInstance().soundManager.play(instance)
                    }
                }
            entry.jobs.add(job)
        } else {
            val instance = createSoundInstance(entry.def, ref, isLooping)
            Minecraft.getInstance().soundManager.play(instance)
            entry.instances.add(instance)
        }
    }

    private fun stopEntry(entry: ActiveEntry) {
        entry.jobs.forEach { job ->
            try {
                job.cancel()
            } catch (e: Exception) {
                "Could not cancel probability job: ${e.message}".err()
            }
        }
        entry.jobs.clear()

        val instancesToStop = entry.instances.toList()
        entry.instances.clear()

        modLaunch {
            instancesToStop.forEach { instance ->
                try {
                    Minecraft.getInstance().soundManager.stop(instance)
                } catch (e: Exception) {
                    "Could not stop sound instance: ${e.message}".err()
                }
            }
        }
    }

    private fun createSoundInstance(
        soundEvent: SoundEventDef,
        referenceSoundInstance: SoundInstance,
        isLooping: Boolean,
    ): SoundInstance =
        when (referenceSoundInstance) {
            is SuppliedSoundInstance -> {
                SimpleTickableSoundInstance(
                    soundEvent.event,
                    soundEvent.source ?: referenceSoundInstance.source,
                    soundEvent.randomSource ?: RandomSource.create(),
                    isLooping,
                    soundEvent.delay ?: referenceSoundInstance.delay,
                    soundEvent.attenuation ?: referenceSoundInstance.attenuation,
                    soundEvent.relative ?: referenceSoundInstance.isRelative,
                    soundEvent.needStream,
                    soundEvent.volumeSupplier ?: referenceSoundInstance.volumeSupplier,
                    soundEvent.pitchSupplier ?: referenceSoundInstance.pitchSupplier,
                    soundEvent.posSupplier ?: referenceSoundInstance.posSupplier,
                    soundEvent.radiusSupplier ?: referenceSoundInstance.radiusSupplier,
                )
            }

            else -> {
                SimpleTickableSoundInstance(
                    soundEvent.event,
                    soundEvent.source ?: referenceSoundInstance.source,
                    soundEvent.randomSource ?: RandomSource.create(),
                    isLooping,
                    soundEvent.delay ?: referenceSoundInstance.delay,
                    soundEvent.attenuation ?: referenceSoundInstance.attenuation,
                    soundEvent.relative ?: referenceSoundInstance.isRelative,
                    soundEvent.needStream,
                    soundEvent.volumeSupplier ?: { referenceSoundInstance.volume },
                    soundEvent.pitchSupplier ?: { referenceSoundInstance.pitch },
                    soundEvent.posSupplier ?: {
                        BlockPos(
                            referenceSoundInstance.x.toInt(),
                            referenceSoundInstance.y.toInt(),
                            referenceSoundInstance.z.toInt(),
                        )
                    },
                    soundEvent.radiusSupplier ?: { 16 },
                )
            }
        }
}
