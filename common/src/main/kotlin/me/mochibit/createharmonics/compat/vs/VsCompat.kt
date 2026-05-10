package me.mochibit.createharmonics.compat.vs

import me.mochibit.createharmonics.audio.instance.StreamingSoundInstance
import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplier
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import org.joml.Matrix4dc
import org.joml.Vector3d
import java.io.InputStream

internal interface VsCompat {
    /**
     * Mutates [currentPosition] if in a ship, projecting to world, global, coordinates.
     */
    fun projectOutOfShip(
        level: Level,
        currentPosition: Vector3d,
    )

    fun isInShip(
        level: Level,
        currentPosition: Vector3d,
    ): Boolean

    fun getShipTransform(
        level: Level,
        currentPosition: Vector3d,
    ): Matrix4dc?
}
