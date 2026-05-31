package me.mochibit.createharmonics.foundation.extension

import me.mochibit.createharmonics.compat.ModCompats
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer

fun ServerPlayer.canInteractWithBlock(
    blockPos: BlockPos,
    rangeCheck: Double,
): Boolean {
    ModCompats.vsCompat?.let {
        if (it.isInShip(this.level(), blockPos.toVector3d()) == true) {
            val dist = it.squaredDistanceToShipIncl(this, blockPos.x.toDouble(), blockPos.y.toDouble(), blockPos.z.toDouble())
            return dist <= rangeCheck * rangeCheck
        }
    }
    return blockPos.closerThan(this.blockPosition(), rangeCheck)
}
