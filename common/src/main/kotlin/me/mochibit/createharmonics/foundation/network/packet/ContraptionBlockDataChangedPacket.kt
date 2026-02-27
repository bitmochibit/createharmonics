package me.mochibit.createharmonics.foundation.network.packet

import me.mochibit.createharmonics.foundation.async.modLaunch
import me.mochibit.createharmonics.foundation.services.contentService
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag

class ContraptionBlockDataChangedPacket(
    val entityID: Int,
    val localPos: BlockPos,
    val newData: CompoundTag,
) : ModPacket,
    S2CPacket {
    override fun handle(context: ModPacket.Context): Boolean {
        modLaunch {
            contentService.contraptionEntityDataChanged(entityID, localPos, newData)
        }
        return true
    }
}
