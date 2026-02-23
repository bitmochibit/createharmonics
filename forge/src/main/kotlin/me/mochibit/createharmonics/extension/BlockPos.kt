package me.mochibit.createharmonics.extension

import net.minecraft.CrashReport
import net.minecraft.CrashReportCategory
import net.minecraft.CrashReportDetail
import net.minecraft.ReportedException
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.LevelChunkSection
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraftforge.fml.ModList
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.getShipManagingPos

fun Level.countLiquidCoveredFaces(
    x: Double,
    y: Double,
    z: Double,
    ship: Ship? = null,
): Pair<Int, Boolean> {
    var liquidCount = 0
    var viscousCount = 0
    var waterCount = 0

    fun accumulateFluid(fluidState: FluidState) {
        liquidCount++
        when {
            fluidState.fluidType.viscosity > 1000 -> viscousCount++
            fluidState.`is`(FluidTags.WATER) -> waterCount++
        }
    }

    // Fast-path: if the center position itself is submerged, the source is fully surrounded —
    // treat all 6 faces as covered immediately without checking any neighbours.

    // Check ship-local center first (cheapest — no transform needed)
    if (ship != null) {
        val shipCenterFluid = getFluidState(x.toInt(), y.toInt(), z.toInt())
        if (!shipCenterFluid.isEmpty) {
            return Direction.entries.size to (shipCenterFluid.fluidType.viscosity > 1000)
        }
    }

    // Check world-space center
    val centerWorldPos: Vector3d =
        if (ship != null) {
            ship.shipToWorld.transformPosition(x, y, z, Vector3d())
        } else {
            Vector3d(x, y, z)
        }

    val centerFluid = getFluidState(centerWorldPos.x.toInt(), centerWorldPos.y.toInt(), centerWorldPos.z.toInt())
    if (!centerFluid.isEmpty) {
        return Direction.entries.size to (centerFluid.fluidType.viscosity > 1000)
    }

    for (direction in Direction.entries) {
        val nx = x + direction.stepX
        val ny = y + direction.stepY
        val nz = z + direction.stepZ

        // 1. World-space check — always takes priority
        val worldNeighbor: Vector3d =
            if (ship != null) {
                ship.shipToWorld.transformPosition(nx, ny, nz, Vector3d())
            } else {
                Vector3d(nx, ny, nz)
            }

        val worldFluid = getFluidState(worldNeighbor.x.toInt(), worldNeighbor.y.toInt(), worldNeighbor.z.toInt())
        if (!worldFluid.isEmpty) {
            accumulateFluid(worldFluid)
            continue
        }

        // 2. If the world block is non-air and non-fluid, it physically blocks this face — skip ship check
        val worldBlock = getBlockState(worldNeighbor.x.toInt(), worldNeighbor.y.toInt(), worldNeighbor.z.toInt())
        if (!worldBlock.isAir) continue

        // 3. Fallback: check ship-local space (handles fluid blocks that are part of the ship itself)
        if (ship != null) {
            val shipFluid = getFluidState(nx.toInt(), ny.toInt(), nz.toInt())
            if (!shipFluid.isEmpty) {
                accumulateFluid(shipFluid)
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

fun Level.getBlockState(
    x: Int,
    y: Int,
    z: Int,
): BlockState {
    val chunk: LevelChunk = this.getChunk(x shr 4, z shr 4)
    try {
        val l: Int = this.getSectionIndex(y)
        if (l >= 0 && l < chunk.sections.size) {
            val levelchunksection: LevelChunkSection = chunk.sections[l]
            if (!levelchunksection.hasOnlyAir()) {
                return levelchunksection.getBlockState(x and 15, y and 15, z and 15)
            }
        }

        return Blocks.AIR.defaultBlockState()
    } catch (throwable: Throwable) {
        val crashreport = CrashReport.forThrowable(throwable, "Getting block state")
        val crashreportcategory = crashreport.addCategory("Block being got")
        crashreportcategory.setDetail(
            "Location",
            CrashReportDetail { CrashReportCategory.formatLocation(this, x, y, z) },
        )
        throw ReportedException(crashreport)
    }
}
