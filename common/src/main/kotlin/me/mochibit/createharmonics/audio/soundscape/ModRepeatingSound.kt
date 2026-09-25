package me.mochibit.createharmonics.audio.soundscape

import net.createmod.catnip.animation.AnimationTickHolder
import net.minecraft.client.Minecraft
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import kotlin.math.max

class ModRepeatingSound(
    private val event: SoundEvent,
    private val scape: ModSoundScape,
    private val sharedPitch: Float,
    private val relativeVolume: Float,
    repeatDelay: Int
) {
    private val repeatDelay: Int

    init {
        this.repeatDelay = max(1, repeatDelay)
    }

    fun tick() {
        if (AnimationTickHolder.getTicks() % repeatDelay != 0) return

        val world = Minecraft.getInstance().level
        val meanPos = scape.getMeanPos()

        world!!.playLocalSound(
            meanPos.x, meanPos.y, meanPos.z, event, SoundSource.AMBIENT,
            scape.volume * relativeVolume, sharedPitch, true
        )
    }
}