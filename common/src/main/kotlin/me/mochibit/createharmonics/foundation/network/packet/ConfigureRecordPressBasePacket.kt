package me.mochibit.createharmonics.foundation.network.packet

import me.mochibit.createharmonics.foundation.services.contentService
import net.minecraft.core.BlockPos

class ConfigureRecordPressBasePacket(
    @Transient private val blockPos: BlockPos,
    val audioUrls: MutableList<String>,
    val urlWeights: MutableList<Float>,
    val randomMode: Boolean = false,
    val newIndex: Int = 0,
) : ModPacket,
    C2SPacket {
    override fun handle(context: ModPacket.Context): Boolean {
        val sender = context.sender ?: return false
        contentService.configureRecordPressBase(sender, blockPos, audioUrls, urlWeights, randomMode, newIndex)
        return true
    }
}
