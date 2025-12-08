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

class MovingStreamSoundInstance(
    inStream: InputStream,
    streamId: StreamId,
    val posSupplier: () -> BlockPos = { BlockPos.ZERO },
    private val radius: Int = 16,
) : StreamingSoundInstance(inStream, streamId),
    TickableSoundInstance {
    private var currentPosition: BlockPos = posSupplier()
    private var stopped = false

    override fun tick() {
        // Update position every tick
        currentPosition = posSupplier()
    }

    override fun isStopped(): Boolean = stopped

    override fun getLocation(): ResourceLocation = "moving_sound_instance".asResource()

    override fun resolve(pManager: SoundManager): WeighedSoundEvents = WeighedSoundEvents(this.location, null)

    override fun getSound(): Sound =
        Sound(
            this.location.toString(),
            ConstantFloat.of(this.volume),
            ConstantFloat.of(this.pitch),
            1,
            Sound.Type.SOUND_EVENT,
            true,
            false,
            radius,
        )

    override fun getSource(): SoundSource = SoundSource.RECORDS

    override fun isLooping(): Boolean = false

    override fun isRelative(): Boolean = false

    override fun getDelay(): Int = 0

    override fun getVolume(): Float = 1.0f

    override fun getPitch(): Float = 1.0f

    override fun getX(): Double = currentPosition.x.toDouble()

    override fun getY(): Double = currentPosition.y.toDouble()

    override fun getZ(): Double = currentPosition.z.toDouble()

    override fun getAttenuation(): SoundInstance.Attenuation = SoundInstance.Attenuation.LINEAR
}
