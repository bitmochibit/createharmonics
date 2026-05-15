package me.mochibit.createharmonics.audio.instance

import me.mochibit.createharmonics.audio.player.AudioPlayer
import me.mochibit.createharmonics.compat.ModCompats
import me.mochibit.createharmonics.foundation.info
import me.mochibit.createharmonics.mixin.SoundEngineAccessor
import me.mochibit.createharmonics.mixin.SoundManagerAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.Sound
import net.minecraft.client.sounds.SoundManager
import net.minecraft.client.sounds.WeighedSoundEvents
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.util.valueproviders.ConstantFloat
import net.minecraft.world.level.Level
import org.joml.Vector3d
import kotlin.math.abs
import kotlin.math.roundToInt

abstract class AudioPlayerSoundInstance(
    private val audioPlayer: AudioPlayer,
    soundEvent: SoundEvent,
    soundSource: SoundSource,
    randomSoundInstance: RandomSource,
    private val streamSound: Boolean,
) : AbstractTickableSoundInstance(soundEvent, soundSource, randomSoundInstance) {
    protected var currentRadius = audioPlayer.masterRadiusInterpolator.getValue()
    protected var currentPitch = audioPlayer.masterPitchInterpolator.getValue()
    protected var currentVolume = audioPlayer.masterVolumeInterpolator.getValue()
    protected var currentPosition = Vector3d()
    private var resolvedSound: Sound? = null

    private val mc = Minecraft.getInstance()
    private val sm = mc.soundManager as SoundManagerAccessor
    protected val engine = sm.soundEngine as SoundEngineAccessor

    private val currentClientLevel: Level? = mc.level

    override fun tick() {
        if (this.isStopped) return

        val ctx = audioPlayer.context ?: return

        ctx.mutatePosition(currentPosition)
        currentPitch = audioPlayer.masterPitchInterpolator.getValue()
        currentVolume = audioPlayer.masterVolumeInterpolator.getValue()
        currentRadius = audioPlayer.masterRadiusInterpolator.getValue()

        if (currentClientLevel != null) {
            ModCompats.sableCompat?.projectOutOfSubLevel(currentClientLevel, currentPosition)
        }

        this.x = currentPosition.x
        this.y = currentPosition.y
        this.z = currentPosition.z

        this.volume = currentVolume

//        this.pitch = currentPitch

        try {
            engine.instanceToChannel[this]?.execute { channel ->
                channel.linearAttenuation(this.currentRadius)
            }
        } catch (e: Exception) {
        }
    }

    override fun resolve(pHandler: SoundManager): WeighedSoundEvents? {
        val weighedSoundEvents = super.resolve(pHandler)

        // For non-streaming sounds, cache the resolved sound and modify its attenuation
        if (!streamSound && weighedSoundEvents != null) {
            resolvedSound = weighedSoundEvents.getSound(this.random)
        }

        return weighedSoundEvents
    }

    override fun getSound(): Sound {
        // For streaming sounds, create custom Sound object
        if (streamSound) {
            return Sound(
                this.location,
                ConstantFloat.of(1.0f),
                ConstantFloat.of(1.0f),
                1,
                Sound.Type.SOUND_EVENT,
                true,
                false,
                currentRadius.toInt(),
            )
        }

        // For non-streaming sounds, use the resolved sound from sounds.json
        // but create a new Sound with our custom attenuation distance
        val baseSound = resolvedSound ?: this.sound

        return Sound(
            baseSound.location,
            baseSound.volume,
            baseSound.pitch,
            baseSound.weight,
            baseSound.type,
            baseSound.shouldStream(),
            baseSound.shouldPreload(),
            currentRadius.toInt(),
        )
    }
}
