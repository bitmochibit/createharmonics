package me.mochibit.createharmonics.audio.instance

import me.mochibit.createharmonics.audio.StreamId
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.client.audio.VelocityTickableSoundInstance
import java.io.InputStream

// VS2 compatible streaming sound instance that moves with a ship and has velocity
class SimpleShipStreamSoundInstance(
    inStream: InputStream,
    streamId: StreamId,
    soundEvent: SoundEvent,
    posSupplier: () -> BlockPos,
    private val ship: Ship,
    volumeSupplier: () -> Float = { 1.0f },
    pitchSupplier: () -> Float = { 1.0f },
    radiusSupplier: () -> Int = { 64 },
    randomSource: RandomSource = RandomSource.create(),
    soundSource: SoundSource = SoundSource.RECORDS,
    looping: Boolean = false,
    delay: Int = 0,
    attenuation: SoundInstance.Attenuation = SoundInstance.Attenuation.LINEAR,
    relative: Boolean = false,
    sampleRate: Int = 44100,
) : StreamingSoundInstance(
        inStream,
        streamId,
        sampleRate,
        soundEvent,
        soundSource,
        randomSource,
        volumeSupplier,
        pitchSupplier,
        posSupplier,
        radiusSupplier,
    ),
    VelocityTickableSoundInstance {
    private val originalPos: Vector3d

    override var velocity: Vector3dc = Vector3d()
        private set

    init {
        this.looping = looping
        this.delay = delay
        this.attenuation = attenuation
        this.relative = relative

        // Store original position from the posSupplier
        val initialPos = posSupplier()
        originalPos = Vector3d(initialPos.x.toDouble(), initialPos.y.toDouble(), initialPos.z.toDouble())

        // Transform to world space initially
        val newPos = ship.shipToWorld.transformPosition(originalPos, Vector3d())
        this.x = newPos.x
        this.y = newPos.y
        this.z = newPos.z
    }

    override fun isStopped(): Boolean = false

    override fun tick() {
        // Update suppliers (but DON'T call super.tick() to avoid position override)
        try {
            currentPitch = pitchSupplier()
            currentVolume = volumeSupplier()
            currentPosition = posSupplier()

            val newRadius = radiusSupplier()
            if (newRadius != currentRadius) {
                currentRadius = newRadius
                engine.instanceToChannel[this]?.execute { channel ->
                    channel.linearAttenuation(this.currentRadius.toFloat())
                }
            }
        } catch (e: Exception) {
            return
        }

        try {
            val shipLocalVec =
                Vector3d(
                    currentPosition.x.toDouble(),
                    currentPosition.y.toDouble(),
                    currentPosition.z.toDouble(),
                )
            val worldPos = ship.shipToWorld.transformPosition(shipLocalVec, Vector3d())

            // Calculate velocity as the difference between new and current position
            this.velocity = Vector3d(worldPos.x - this.x, worldPos.y - this.y, worldPos.z - this.z)

            // Update position to world coordinates
            this.x = worldPos.x
            this.y = worldPos.y
            this.z = worldPos.z
        } catch (e: Exception) {
            // If ship transform fails, keep using last position
            this.velocity = Vector3d(0.0, 0.0, 0.0)
        }

        // Update volume and pitch
        this.volume = currentVolume
        this.pitch = currentPitch
    }
}
