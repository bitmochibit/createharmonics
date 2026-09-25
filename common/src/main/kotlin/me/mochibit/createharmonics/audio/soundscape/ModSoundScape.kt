package me.mochibit.createharmonics.audio.soundscape

import com.simibubi.create.infrastructure.config.AllConfigs
import net.createmod.catnip.animation.AnimationTickHolder
import net.createmod.catnip.math.VecHelper
import net.minecraft.client.Minecraft
import net.minecraft.sounds.SoundEvent
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3


class ModSoundScape(private val pitch: Float, private val group: ModSoundScapes.AmbienceGroup) {
    private val pitchGroup = ModSoundScapes.getGroupFromPitch(pitch)

    private val continuousSounds = mutableListOf<ModContinuousSound>()
    private val repeatingSounds = mutableListOf<ModRepeatingSound>()

    private var cachedMeanPos: Vec3? = null

    fun continuous(sound: SoundEvent, relativeVolume: Float, relativePitch: Float): ModSoundScape = apply {
        continuousSounds += ModContinuousSound(sound, this, pitch * relativePitch, relativeVolume)
    }

    fun repeating(sound: SoundEvent, relativeVolume: Float, relativePitch: Float, delay: Int): ModSoundScape = apply {
        repeatingSounds += ModRepeatingSound(sound, this, pitch * relativePitch, relativeVolume, delay)
    }

    fun play() {
        continuousSounds.forEach { Minecraft.getInstance().soundManager.play(it) }
    }

    fun tick() {
        if (AnimationTickHolder.getTicks() % ModSoundScapes.UPDATE_INTERVAL == 0) cachedMeanPos = null
        repeatingSounds.forEach(ModRepeatingSound::tick)
    }

    fun remove() {
        continuousSounds.forEach(ModContinuousSound::remove)
    }

    fun getMeanPos(): Vec3 = cachedMeanPos ?: computeMeanPos().also { cachedMeanPos = it }

    private fun computeMeanPos(): Vec3 {
        val locations = ModSoundScapes.getAllLocations(group, pitchGroup)
        if (locations.isEmpty()) return Vec3.ZERO
        val sum = locations.fold(Vec3.ZERO) { acc, pos -> acc.add(VecHelper.getCenterOf(pos)) }
        return sum.scale(1.0 / locations.size)
    }

    val volume: Float
        get() {
            val camera = Minecraft.getInstance().cameraEntity
            val distanceMultiplier = camera?.let {
                val distance = it.position().distanceTo(getMeanPos())
                Mth.lerp(distance / ModSoundScapes.MAX_AMBIENT_SOURCE_DISTANCE, 2.0, 0.0).toFloat()
            } ?: 0f

            val soundCount = ModSoundScapes.getSoundCount(group, pitchGroup)
            val max = AllConfigs.client().ambientVolumeCap.f
            val argMax = ModSoundScapes.SOUND_VOLUME_ARG_MAX.toFloat()
            return Mth.clamp(soundCount / (argMax * 10f), 0.025f, max) * distanceMultiplier
        }
}
