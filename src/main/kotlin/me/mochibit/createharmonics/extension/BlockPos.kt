package me.mochibit.createharmonics.extension

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.Level
import net.minecraftforge.fml.ModList
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.getShipManagingPos
import thedarkcolour.kotlinforforge.forge.vectorutil.v3d.toVec3
import thedarkcolour.kotlinforforge.forge.vectorutil.v3d.toVector3d

fun BlockPos.countLiquidCoveredFaces(
    level: Level,
    ship: Ship? = null,
): Pair<Int, Boolean> {
    var liquidCount = 0
    var viscousCount = 0
    var waterCount = 0

    for (direction in Direction.entries) {
        val relativePos = this.relative(direction)

        // Transform to world coordinates if on a ship
        val checkPos =
            if (ship != null) {
                val worldPos = ship.shipToWorld.transformPosition(relativePos.toVector3d(), Vector3d())
                BlockPos.containing(worldPos.toVec3())
            } else {
                relativePos
            }

        val fluidState = level.getFluidState(checkPos)

        if (!fluidState.isEmpty) {
            liquidCount++

            when {
                fluidState.fluidType.viscosity > 1000 -> viscousCount++
                fluidState.`is`(FluidTags.WATER) -> waterCount++
            }
        }
    }

    val isThick = viscousCount >= waterCount && viscousCount > 0

    return liquidCount to isThick
}

fun BlockPos.getManagingShip(level: Level): Ship? {
    if (!ModList.get().isLoaded("valkyrienskies")) return null
    return level.getShipManagingPos(
        this.x.toDouble(),
        this.y.toDouble(),
        this.z.toDouble(),
    )
}
