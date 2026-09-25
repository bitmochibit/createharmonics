package me.mochibit.createharmonics.audio.soundscape

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource

open class ModContinuousSound(
    event: SoundEvent,
    private val scape: ModSoundScape,
    private val sharedPitch: Float,
    private val relativeVolume: Float
) : AbstractTickableSoundInstance(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom()) {
    init {
        this.looping = true
        this.delay = 0
        this.relative = false
    }

    fun remove() {
        stop()
    }

    override fun getVolume(): Float {
        return scape.volume * relativeVolume
    }

    override fun getPitch(): Float {
        return sharedPitch
    }

    override fun getX(): Double {
        return scape.getMeanPos().x
    }

    override fun getY(): Double {
        return scape.getMeanPos().y
    }

    override fun getZ(): Double {
        return scape.getMeanPos().z
    }

    override fun tick() {}
}