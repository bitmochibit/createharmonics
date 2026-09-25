package me.mochibit.createharmonics.audio.soundscape

import com.simibubi.create.infrastructure.config.AllConfigs
import net.createmod.catnip.animation.AnimationTickHolder
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvents
import java.util.*
import kotlin.to

object ModSoundScapes {
    const val MAX_AMBIENT_SOURCE_DISTANCE: Int = 16
    const val UPDATE_INTERVAL: Int = 5
    const val SOUND_VOLUME_ARG_MAX: Int = 15

    private fun resonating(pitch: Float, group: AmbienceGroup): ModSoundScape =
        ModSoundScape(pitch, group).continuous(SoundEvents.BEACON_AMBIENT, 2f, .2f)

    private val counter = EnumMap<AmbienceGroup, MutableMap<PitchGroup, MutableSet<BlockPos>>>(AmbienceGroup::class.java)
    private val activeSounds = mutableMapOf<Pair<AmbienceGroup, PitchGroup>, ModSoundScape>()

    fun play(group: AmbienceGroup, pos: BlockPos, pitch: Float) {
        if (!AllConfigs.client().enableAmbientSounds.get()) return
        if (!outOfRange(pos)) addSound(group, pos, pitch)
    }

    fun tick() {
        activeSounds.values.forEach(ModSoundScape::tick)

        if (AnimationTickHolder.getTicks() % UPDATE_INTERVAL != 0) return

        val disabled = !AllConfigs.client().enableAmbientSounds.get()
        activeSounds.entries.removeAll { (key, sound) ->
            val (group, pitchGroup) = key
            val shouldRemove = disabled || getSoundCount(group, pitchGroup) == 0
            if (shouldRemove) sound.remove()
            shouldRemove
        }

        counter.values.forEach { byPitch -> byPitch.values.forEach(MutableSet<BlockPos>::clear) }
    }

    private fun addSound(group: AmbienceGroup, pos: BlockPos, pitch: Float) {
        val pitchGroup = getGroupFromPitch(pitch)
        counter.getOrPut(group) { EnumMap(PitchGroup::class.java) }
            .getOrPut(pitchGroup) { mutableSetOf() }
            .add(pos)

        activeSounds.getOrPut(group to pitchGroup) {
            group.instantiate(pitch).also { it.play() }
        }
    }

    fun invalidateAll() {
        counter.clear()
        activeSounds.values.forEach(ModSoundScape::remove)
        activeSounds.clear()
    }

    internal fun outOfRange(pos: BlockPos): Boolean =
        !cameraPos.closerThan(pos, MAX_AMBIENT_SOURCE_DISTANCE.toDouble())

    internal val cameraPos: BlockPos
        get() = Minecraft.getInstance().cameraEntity?.blockPosition() ?: BlockPos.ZERO

    fun getSoundCount(group: AmbienceGroup, pitchGroup: PitchGroup): Int =
        getAllLocations(group, pitchGroup).size

    fun getAllLocations(group: AmbienceGroup, pitchGroup: PitchGroup): Set<BlockPos> =
        counter[group]?.get(pitchGroup) ?: emptySet()

    fun getGroupFromPitch(pitch: Float): PitchGroup = when {
        pitch < .70f -> PitchGroup.VERY_LOW
        pitch < .90f -> PitchGroup.LOW
        pitch < 1.10f -> PitchGroup.NORMAL
        pitch < 1.30f -> PitchGroup.HIGH
        else -> PitchGroup.VERY_HIGH
    }

    enum class AmbienceGroup(private val factory: (Float, AmbienceGroup) -> ModSoundScape) {
        RESONATING({ pitch, group -> resonating(pitch, group) });

        fun instantiate(pitch: Float): ModSoundScape = factory(pitch, this)
    }

    enum class PitchGroup {
        VERY_LOW, LOW, NORMAL, HIGH, VERY_HIGH
    }
}



