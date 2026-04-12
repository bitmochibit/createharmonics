package me.mochibit.createharmonics.foundation.network.packet

import com.simibubi.create.foundation.utility.AdventureUtil
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import me.mochibit.createharmonics.content.processing.recordPressBase.RecordPressBaseBlockEntity
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
        if (sender.isSpectator || AdventureUtil.isAdventure(sender)) return false
        val world = sender.level()
        if (world == null || !world.isLoaded(blockPos)) return false
        if (!blockPos.closerThan(sender.blockPosition(), 20.0)) return false
        val blockEntity = world.getBlockEntity(blockPos)
        if (blockEntity is RecordPressBaseBlockEntity) {
            blockEntity.audioUrls = audioUrls
            blockEntity.urlWeights = urlWeights
            blockEntity.randomMode = randomMode
            blockEntity.currentUrlIndex = newIndex
            blockEntity.sendData()
            blockEntity.setChanged()
        }
        return true
    }
}
