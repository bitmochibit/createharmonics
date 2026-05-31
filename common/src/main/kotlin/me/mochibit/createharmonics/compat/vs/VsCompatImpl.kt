package me.mochibit.createharmonics.compat.vs

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import org.joml.Matrix4dc
import org.joml.Vector3d
import org.valkyrienskies.mod.common.VSClientGameUtils
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.squaredDistanceToInclShips

internal object VsCompatImpl : VsCompat {
    override fun projectOutOfShip(
        level: Level,
        currentPosition: Vector3d,
    ) {
        val ship = level.getShipManagingPos(currentPosition) ?: return
        val transform = ship.shipToWorld
        transform.transformPosition(currentPosition)
    }

    override fun getShipTransform(
        level: Level,
        currentPosition: Vector3d,
    ): Matrix4dc? {
        val ship = level.getShipManagingPos(currentPosition) ?: return null
        return ship.shipToWorld
    }

    override fun squaredDistanceToShipIncl(
        serverPlayer: ServerPlayer,
        x: Double,
        y: Double,
        z: Double,
    ): Double = serverPlayer.squaredDistanceToInclShips(x, y, z)

    override fun isInShip(
        level: Level,
        currentPosition: Vector3d,
    ): Boolean {
        val ship = level.getShipManagingPos(currentPosition)
        return ship != null
    }
}
