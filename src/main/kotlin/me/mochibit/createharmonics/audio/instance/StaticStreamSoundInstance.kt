package me.mochibit.createharmonics.audio.instance

import me.mochibit.createharmonics.asResource
import me.mochibit.createharmonics.audio.StreamId
import net.minecraft.client.resources.sounds.Sound
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.SoundManager
import net.minecraft.client.sounds.WeighedSoundEvents
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import net.minecraft.util.valueproviders.ConstantFloat
import java.io.InputStream

class StaticStreamSoundInstance(
    inStream: InputStream,
    streamId: StreamId,
    private val position: BlockPos,
    private val radius: Int = 16,
) : StreamingSoundInstance(inStream, streamId) {
    override fun getLocation(): ResourceLocation = "static_sound_instance".asResource()

    override fun resolve(soundManager: SoundManager): WeighedSoundEvents = WeighedSoundEvents(this.location, null)

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

    override fun getX(): Double = position.x.toDouble()

    override fun getY(): Double = position.y.toDouble()

    override fun getZ(): Double = position.z.toDouble()

    override fun getAttenuation(): SoundInstance.Attenuation = SoundInstance.Attenuation.LINEAR
}
