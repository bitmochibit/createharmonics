package me.mochibit.createharmonics.audio.instance

import me.mochibit.createharmonics.audio.stream.PcmAudioStream
import me.mochibit.createharmonics.compat.VSCompat
import me.mochibit.createharmonics.foundation.extension.asResource
import me.mochibit.createharmonics.foundation.services.platformService
import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplier
import net.minecraft.client.sounds.AudioStream
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import java.io.InputStream
import java.util.Random
import java.util.concurrent.CompletableFuture

abstract class StreamingSoundInstance(
    val sourceStream: InputStream,
    val streamId: String,
    override var sampleRate: Int = 44100,
    soundEvent: SoundEvent,
    soundSource: SoundSource = SoundSource.RECORDS,
    randomSource: RandomSource = RandomSource.create(),
    volumeSupplier: FloatSupplier = FloatSupplier { 1.0f },
    pitchSupplier: FloatSupplier = FloatSupplier { 1.0f },
    posSupplier: () -> BlockPos = { BlockPos.ZERO },
    radiusSupplier: FloatSupplier = FloatSupplier { 64f },
) : SuppliedSoundInstance(
        soundEvent,
        soundSource,
        randomSource,
        true,
        posSupplier,
        volumeSupplier,
        pitchSupplier,
        radiusSupplier,
    ),
    SampleRatedInstance {
    override fun getLocation(): ResourceLocation = "streaming_sound_instance".asResource()

    companion object {
        fun simpleFactory(
            stream: InputStream,
            streamId: String,
            soundEvent: SoundEvent,
            sampleRate: Int = 44100,
            soundSource: SoundSource = SoundSource.RECORDS,
            randomSource: RandomSource = RandomSource.create(),
            volumeSupplier: FloatSupplier = FloatSupplier { 1.0f },
            pitchSupplier: FloatSupplier = FloatSupplier { 1.0f },
            posSupplier: () -> BlockPos = { BlockPos.ZERO },
            radiusSupplier: FloatSupplier = FloatSupplier { 64f },
            checkForVsShip: Boolean = false,
            vsLevelCheck: Level? = null,
            vsBeCheck: BlockEntity? = null,
        ): StreamingSoundInstance {
            if (checkForVsShip &&
                vsLevelCheck != null &&
                vsBeCheck != null &&
                platformService.isModLoaded("valkyrienskies")
            ) {
                val shipInstance =
                    VSCompat.tryCreateShipSoundInstance(
                        stream,
                        streamId,
                        soundEvent,
                        sampleRate,
                        soundSource,
                        randomSource,
                        volumeSupplier,
                        pitchSupplier,
                        posSupplier,
                        radiusSupplier,
                        vsLevelCheck,
                        vsBeCheck,
                    )
                if (shipInstance != null) return shipInstance
            }

            return SimpleStreamSoundInstance(
                stream,
                streamId,
                soundEvent,
                posSupplier,
                radiusSupplier,
                pitchSupplier,
                radiusSupplier,
                randomSource,
                soundSource,
                sampleRate = sampleRate,
            )
        }
    }

    val currentAudioStreamDelegate =
        lazy {
            PcmAudioStream(this.sourceStream, this.sampleRate)
        }
    val currentAudioStream: AudioStream by currentAudioStreamDelegate
}
