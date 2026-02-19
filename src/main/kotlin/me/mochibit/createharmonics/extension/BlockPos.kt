package me.mochibit.createharmonics.extension

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.SectionPos
import net.minecraft.core.Vec3i
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.Vec3
import net.minecraftforge.fml.ModList
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.getShipManagingPos
import thedarkcolour.kotlinforforge.forge.vectorutil.v3d.toVec3
import thedarkcolour.kotlinforforge.forge.vectorutil.v3d.toVec3i
import thedarkcolour.kotlinforforge.forge.vectorutil.v3d.toVector3d

fun Level.countLiquidCoveredFaces(
    x: Double,
    y: Double,
    z: Double,
    ship: Ship? = null,
): Pair<Int, Boolean> {
    var liquidCount = 0
    var viscousCount = 0
    var waterCount = 0

    for (direction in Direction.entries) {
        val nx = x + direction.stepX
        val ny = y + direction.stepY
        val nz = z + direction.stepZ

        val (cx, cy, cz) =
            if (ship != null) {
                val worldPos = ship.shipToWorld.transformPosition(nx, ny, nz, Vector3d())
                Triple(worldPos.x, worldPos.y, worldPos.z)
            } else {
                Triple(nx, ny, nz)
            }

        val fluidState = getFluidState(cx.toInt(), cy.toInt(), cz.toInt())

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

fun Level.getFluidState(
    x: Int,
    y: Int,
    z: Int,
): FluidState {
    if (this.isOutsideBuildHeight(y)) {
        return Fluids.EMPTY.defaultFluidState()
    } else {
        val chunk: LevelChunk = this.getChunk(x shr 4, z shr 4)
        return chunk.getFluidState(x, y, z)
    }
}
