package me.mochibit.createharmonics.foundation.extension

import com.simibubi.create.infrastructure.ponder.scenes.fluid.HosePulleyScenes.level
import me.mochibit.createharmonics.audio.effect.EffectPreset
import me.mochibit.createharmonics.foundation.services.contentService
import me.mochibit.createharmonics.foundation.services.platformService
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
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.getShipManagingPos

data class BlockCounts(
    val roomIncreasers: Int,
    val dampingIncreasers: Int,
    val wetIncreasers: Int,
) {
    val total: Int get() = roomIncreasers + dampingIncreasers + wetIncreasers
}

fun Level.scanReverberatorBlocks(
    x: Double,
    y: Double,
    z: Double,
    scanRadius: Int,
    checkForShip: Boolean = true,
): BlockCounts {
    val ship =
        if (checkForShip) {
            this.getShipManagingPos(x, y, z)
        } else {
            null
        }

    var roomIncreasers = 0
    var dampingIncreasers = 0
    var wetIncreasers = 0

    for (dx in -scanRadius..scanRadius) {
        for (dy in -scanRadius..scanRadius) {
            for (dz in -scanRadius..scanRadius) {
                val nx = x + dx
                val ny = y + dy
                val nz = z + dz

                // World-space check — transform ship-local coords to world if on a ship
                val worldPos: Vector3d =
                    if (ship != null) {
                        ship.shipToWorld.transformPosition(nx, ny, nz, Vector3d())
                    } else {
                        Vector3d(nx, ny, nz)
                    }

                val blockState =
                    this.getBlockState(
                        worldPos.x.toInt(),
                        worldPos.y.toInt(),
                        worldPos.z.toInt(),
                    )

                when (blockState.block) {
                    in EffectPreset.Reverberator.ROOM_INCREASERS_BLOCKS -> roomIncreasers++
                    in EffectPreset.Reverberator.DAMPING_INCREASERS_BLOCKS -> dampingIncreasers++
                    in EffectPreset.Reverberator.WET_INCREASERS_BLOCKS -> wetIncreasers++
                }

                // Fallback: check ship-local space for blocks that are part of the contraption itself
                if (ship != null) {
                    val shipBlockState = this.getBlockState(nx.toInt(), ny.toInt(), nz.toInt())
                    when (shipBlockState.block) {
                        Blocks.AMETHYST_BLOCK -> roomIncreasers++
                        Blocks.EMERALD_BLOCK -> dampingIncreasers++
                        Blocks.DIAMOND_BLOCK -> wetIncreasers++
                    }
                }
            }
        }
    }

    return BlockCounts(roomIncreasers, dampingIncreasers, wetIncreasers)
}

fun Level.countLiquidCoveredFaces(
    x: Double,
    y: Double,
    z: Double,
    checkForShip: Boolean = true,
): Pair<Int, Boolean> {
    val ship =
        if (checkForShip) {
            this.getShipManagingPos(x, y, z)
        } else {
            null
        }

    var liquidCount = 0
    var viscousCount = 0
    var waterCount = 0

    fun accumulateFluid(fluidState: FluidState) {
        liquidCount++
        when {
            contentService.getViscosity(fluidState) > 1000 -> viscousCount++
            fluidState.`is`(FluidTags.WATER) -> waterCount++
        }
    }

    // Fast-path: if the center position itself is submerged, the source is fully surrounded —
    // treat all 6 faces as covered immediately without checking any neighbours.

    // Check ship-local center first (cheapest — no transform needed)
    if (ship != null) {
        val shipCenterFluid = getFluidState(x.toInt(), y.toInt(), z.toInt())
        if (!shipCenterFluid.isEmpty) {
            return Direction.entries.size to (contentService.getViscosity(shipCenterFluid) > 1000)
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
        return Direction.entries.size to (contentService.getViscosity(centerFluid) > 1000)
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
    if (!platformService.isModLoaded("valkyrienskies")) return null
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
            val levelChunkSection: LevelChunkSection = chunk.sections[l]
            if (!levelChunkSection.hasOnlyAir()) {
                return levelChunkSection.getBlockState(x and 15, y and 15, z and 15)
            }
        }

        return Blocks.AIR.defaultBlockState()
    } catch (throwable: Throwable) {
        val crashReport = CrashReport.forThrowable(throwable, "Getting block state")
        val crashReportCategory = crashReport.addCategory("Block being got")
        crashReportCategory.setDetail(
            "Location",
        ) { CrashReportCategory.formatLocation(this, x, y, z) }
        throw ReportedException(crashReport)
    }
}
