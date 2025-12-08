package me.mochibit.createharmonics.audio.comp

import me.mochibit.createharmonics.audio.instance.MovingStreamSoundInstance
import me.mochibit.createharmonics.audio.instance.SimpleMovingSoundInstance
import me.mochibit.createharmonics.audio.instance.StaticStreamSoundInstance
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource

class SoundEventComposition(
    val soundList: List<SoundEventDef> = listOf(),
) {
    data class SoundEventDef(
        val event: SoundEvent,
        val source: SoundSource? = null,
        val volume: Float? = null,
        val pitch: Float? = null,
        val randomSource: RandomSource? = null,
        val looping: Boolean? = null,
        val delay: Int? = null,
        val attenuation: SoundInstance.Attenuation? = null,
        val x: Double? = null,
        val y: Double? = null,
        val z: Double? = null,
        val posSupplier: (() -> BlockPos)? = null,
        val relative: Boolean? = null,
    )

    private val soundInstances: MutableList<SoundInstance> = mutableListOf()

    fun makeComposition(referenceSoundInstance: SoundInstance) {
        for (soundEvent in soundList) {
            val newSoundInstance =
                when (referenceSoundInstance) {
                    is MovingStreamSoundInstance -> {
                        SimpleMovingSoundInstance(
                            soundEvent.event,
                            soundEvent.source ?: referenceSoundInstance.source,
                            soundEvent.volume ?: referenceSoundInstance.volume,
                            soundEvent.pitch ?: referenceSoundInstance.pitch,
                            soundEvent.randomSource ?: RandomSource.create(),
                            soundEvent.looping ?: referenceSoundInstance.isLooping,
                            soundEvent.delay ?: referenceSoundInstance.delay,
                            soundEvent.attenuation ?: referenceSoundInstance.attenuation,
                            soundEvent.relative ?: referenceSoundInstance.isRelative,
                            soundEvent.posSupplier ?: referenceSoundInstance.posSupplier,
                        )
                    }

                    else -> {
                        SimpleSoundInstance(
                            soundEvent.event.location,
                            soundEvent.source ?: referenceSoundInstance.source,
                            soundEvent.volume ?: referenceSoundInstance.volume,
                            soundEvent.pitch ?: referenceSoundInstance.pitch,
                            soundEvent.randomSource ?: RandomSource.create(),
                            soundEvent.looping ?: referenceSoundInstance.isLooping,
                            soundEvent.delay ?: referenceSoundInstance.delay,
                            soundEvent.attenuation ?: referenceSoundInstance.attenuation,
                            soundEvent.x ?: referenceSoundInstance.x,
                            soundEvent.y ?: referenceSoundInstance.y,
                            soundEvent.z ?: referenceSoundInstance.z,
                            soundEvent.relative ?: referenceSoundInstance.isRelative,
                        )
                    }
                }

            Minecraft.getInstance().soundManager.play(newSoundInstance)
            soundInstances.add(newSoundInstance)
        }
    }

    fun stopComposition() {
        for (soundInstance in soundInstances) {
            Minecraft.getInstance().soundManager.stop(soundInstance)
        }
    }
}
