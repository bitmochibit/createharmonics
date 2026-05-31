package me.mochibit.createharmonics.compat.vs

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import org.joml.Matrix4dc
import org.joml.Vector3d

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

    fun squaredDistanceToShipIncl(
        serverPlayer: ServerPlayer,
        x: Double,
        y: Double,
        z: Double,
    ): Double
}
