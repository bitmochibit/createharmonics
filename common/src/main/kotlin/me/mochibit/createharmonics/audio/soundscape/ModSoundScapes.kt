package me.mochibit.createharmonics.audio.soundscape

import com.simibubi.create.infrastructure.config.AllConfigs
import net.createmod.catnip.animation.AnimationTickHolder
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvents
import java.util.*
import kotlin.to

/**
 * Mod sound scapes
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new ModSoundScapes
 */
object ModSoundScapes {
    const val MAX_AMBIENT_SOURCE_DISTANCE: Int = 16
    const val UPDATE_INTERVAL: Int = 5
    const val SOUND_VOLUME_ARG_MAX: Int = 15

    private fun resonating(pitch: Float, group: AmbienceGroup): ModSoundScape =
        ModSoundScape(pitch, group).continuous(SoundEvents.BEACON_AMBIENT, 2f, .2f)

    private val counter = EnumMap<AmbienceGroup, MutableMap<PitchGroup, MutableSet<BlockPos>>>(AmbienceGroup::class.java)
    private val activeSounds = mutableMapOf<Pair<AmbienceGroup, PitchGroup>, ModSoundScape>()

    /**
     * Play
     *
     * @param group
     * @param pos
     * @param pitch
     */
    fun play(group: AmbienceGroup, pos: BlockPos, pitch: Float) {
        if (!AllConfigs.client().enableAmbientSounds.get()) return
        if (!outOfRange(pos)) addSound(group, pos, pitch)
    }

    /**
     * Tick
     *
     */
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

    /**
     * Invalidate all
     *
     */
    fun invalidateAll() {
        counter.clear()
        activeSounds.values.forEach(ModSoundScape::remove)
        activeSounds.clear()
    }

    /**
     * Out of range
     *
     * @param pos
     * @return `true` if the condition holds, `false` otherwise
     */
    internal fun outOfRange(pos: BlockPos): Boolean =
        !cameraPos.closerThan(pos, MAX_AMBIENT_SOURCE_DISTANCE.toDouble())

    internal val cameraPos: BlockPos
        get() = Minecraft.getInstance().cameraEntity?.blockPosition() ?: BlockPos.ZERO

    /**
     * Returns the sound count
     *
     * @param group
     * @param pitchGroup the pitch group
     * @return the int
     */
    fun getSoundCount(group: AmbienceGroup, pitchGroup: PitchGroup): Int =
        getAllLocations(group, pitchGroup).size

    /**
     * Returns the all locationses
     *
     * @param group
     * @param pitchGroup the pitch group
     * @return the block poses
     */
    fun getAllLocations(group: AmbienceGroup, pitchGroup: PitchGroup): Set<BlockPos> =
        counter[group]?.get(pitchGroup) ?: emptySet()

    /**
     * Returns the group from pitch
     *
     * @param pitch
     * @return the pitch group
     */
    fun getGroupFromPitch(pitch: Float): PitchGroup = when {
        pitch < .70f -> PitchGroup.VERY_LOW
        pitch < .90f -> PitchGroup.LOW
        pitch < 1.10f -> PitchGroup.NORMAL
        pitch < 1.30f -> PitchGroup.HIGH
        else -> PitchGroup.VERY_HIGH
    }

    /**
     * Ambience group
     * Enum — a fixed set of named constants.
     *
     * @constructor Creates a new AmbienceGroup
     * @property factory
     */
    enum class AmbienceGroup(private val factory: (Float, AmbienceGroup) -> ModSoundScape) {
        /**
         * Resonating
         *
         * @constructor Creates a new RESONATING
         */
        RESONATING({ pitch, group -> resonating(pitch, group) });

        /**
         * Instantiate
         *
         * @param pitch
         * @return the mod sound scape
         */
        fun instantiate(pitch: Float): ModSoundScape = factory(pitch, this)
    }

    /**
     * Pitch group
     * Enum — a fixed set of named constants.
     *
     * @constructor Creates a new PitchGroup
     */
    enum class PitchGroup {
        /**
         * Very low
         *
         * @constructor Creates a new VERY_LOW
         */
        VERY_LOW,

        /**
         * Low
         *
         * @constructor Creates a new LOW
         */
        LOW,

        /**
         * Normal
         *
         * @constructor Creates a new NORMAL
         */
        NORMAL,

        /**
         * High
         *
         * @constructor Creates a new HIGH
         */
        HIGH,

        /**
         * Very high
         *
         * @constructor Creates a new VERY_HIGH
         */
        VERY_HIGH
    }
}



