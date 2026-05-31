package me.mochibit.createharmonics.foundation.extension

import net.minecraft.CrashReport
import net.minecraft.CrashReportCategory
import net.minecraft.ReportedException
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.LevelChunkSection
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import org.joml.Vector3d

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

fun BlockPos.toVector3d(): Vector3d = Vector3d(this.x.toDouble(), this.y.toDouble(), this.z.toDouble())
