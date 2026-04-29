package me.mochibit.createharmonics.compat

import me.mochibit.createharmonics.audio.instance.SimpleShipStreamSoundInstance
import me.mochibit.createharmonics.audio.instance.StreamingSoundInstance
import me.mochibit.createharmonics.foundation.services.platformService
import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplier
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.getShipManagingPos
import java.io.InputStream

internal object VSCompat {
    /**
     * If (x, y, z) is managed by a VS ship, returns a function that converts
     * ship-local coordinates → world coordinates.
     * Returns null when not on a ship.
     */
    fun getShipTransform(
        level: Level,
        x: Double,
        y: Double,
        z: Double,
    ): ((Double, Double, Double) -> Vector3d)? {
        val ship = level.getShipManagingPos(x, y, z) ?: return null
        val transform = ship.shipToWorld
        return { lx, ly, lz -> transform.transformPosition(lx, ly, lz, Vector3d()) }
    }

    fun getManagingShip(
        level: Level,
        blockPos: BlockPos,
    ): Ship? {
        if (!platformService.isModLoaded("valkyrienskies")) return null
        return level.getShipManagingPos(
            blockPos.x.toDouble(),
            blockPos.y.toDouble(),
            blockPos.z.toDouble(),
        )
    }

    fun tryCreateShipSoundInstance(
        stream: InputStream,
        streamId: String,
        soundEvent: SoundEvent,
        sampleRate: Int,
        soundSource: SoundSource,
        randomSource: RandomSource,
        volumeSupplier: FloatSupplier,
        pitchSupplier: FloatSupplier,
        posSupplier: () -> BlockPos,
        radiusSupplier: FloatSupplier,
        looping: Boolean = false,
        attenuation: SoundInstance.Attenuation = SoundInstance.Attenuation.LINEAR,
        delay: Int = 0,
        relative: Boolean = false,
        level: Level,
        blockEntity: BlockEntity,
    ): StreamingSoundInstance? {
        val ship = getManagingShip(level, blockEntity.blockPos) ?: return null
        return SimpleShipStreamSoundInstance(
            stream,
            streamId,
            soundEvent,
            posSupplier,
            ship,
            volumeSupplier,
            pitchSupplier,
            radiusSupplier,
            randomSource,
            soundSource,
            looping,
            delay,
            attenuation,
            relative,
            sampleRate,
        )
    }
}
