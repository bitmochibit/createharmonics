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

    for (direction in Direction.entries) {
        val nx = x + direction.stepX
        val ny = y + direction.stepY
        val nz = z + direction.stepZ

        if (ship != null) {
            val shipFluidState = getFluidState(nx.toInt(), ny.toInt(), nz.toInt())

            if (!shipFluidState.isEmpty) {
                // There's fluid on the ship itself covering this face
                liquidCount++
                when {
                    shipFluidState.fluidType.viscosity > 1000 -> viscousCount++
                    shipFluidState.`is`(FluidTags.WATER) -> waterCount++
                }
                continue
            }

            // If the ship block is not air and not fluid, it's blocking — skip
            val shipBlock = getBlockState(nx.toInt(), ny.toInt(), nz.toInt())
            if (!shipBlock.isAir) continue
        }

        // Check world-space fluid
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
