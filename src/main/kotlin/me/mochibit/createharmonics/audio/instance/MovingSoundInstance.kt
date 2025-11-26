package me.mochibit.createharmonics.audio.instance

import me.mochibit.createharmonics.asResource
import me.mochibit.createharmonics.audio.StreamId
import net.minecraft.client.resources.sounds.Sound
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.resources.sounds.TickableSoundInstance
import net.minecraft.client.sounds.SoundManager
import net.minecraft.client.sounds.WeighedSoundEvents
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import net.minecraft.util.valueproviders.ConstantFloat
import java.io.InputStream

class MovingSoundInstance(
    inStream: InputStream,
    streamId: StreamId,
    private val posSupplier: () -> BlockPos = { BlockPos.ZERO },
    private val radius: Int = 16,
) : StreamingSoundInstance(inStream, streamId), TickableSoundInstance {
    private var currentPosition: BlockPos = posSupplier()
    private var stopped = false

    override fun tick() {
        // Update position every tick
        currentPosition = posSupplier()
    }

    override fun isStopped(): Boolean {
        return stopped
    }


    override fun getLocation(): ResourceLocation {
        return "moving_sound_instance".asResource()
    }

    override fun resolve(pManager: SoundManager): WeighedSoundEvents {
        return WeighedSoundEvents(this.location, null)
    }

    override fun getSound(): Sound {
        return Sound(
            this.location.toString(),
            ConstantFloat.of(this.volume),
            ConstantFloat.of(this.pitch),
            1,
            Sound.Type.SOUND_EVENT,
            true,
            false,
            radius
        )
    }

    override fun getSource(): SoundSource {
        return SoundSource.RECORDS
    }

    override fun isLooping(): Boolean {
        return false
    }

    override fun isRelative(): Boolean {
        return false
    }

    override fun getDelay(): Int {
        return 0
    }

    override fun getVolume(): Float {
        return 1.0f
    }

    override fun getPitch(): Float {
        return 1.0f
    }

    override fun getX(): Double {
        return currentPosition.x.toDouble()
    }

    override fun getY(): Double {
        return currentPosition.y.toDouble()
    }

    override fun getZ(): Double {
        return currentPosition.z.toDouble()
    }

    override fun getAttenuation(): SoundInstance.Attenuation {
        return SoundInstance.Attenuation.LINEAR
    }

}