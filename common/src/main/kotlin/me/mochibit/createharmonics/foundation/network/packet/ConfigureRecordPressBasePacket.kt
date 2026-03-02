package me.mochibit.createharmonics.foundation.network.packet

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import me.mochibit.createharmonics.foundation.services.contentService
import net.minecraft.core.BlockPos

@Serializable
class ConfigureRecordPressBasePacket(
    @Contextual val blockPos: BlockPos,
    val audioUrls: MutableList<String>,
    val urlWeights: MutableList<Float>,
    val randomMode: Boolean,
    val newIndex: Int,
) : ModPacket,
    C2SPacket {
    override fun handle(context: ModPacket.Context): Boolean {
        val sender = context.sender ?: return false
        contentService.configureRecordPressBase(sender, blockPos, audioUrls, urlWeights, randomMode, newIndex)
        return true
    }
}
